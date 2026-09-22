package it.visiovoice.api;

import it.visiovoice.agents.AgentContext;
import it.visiovoice.agents.NarrationPort;
import it.visiovoice.agents.PerceptionPort;
import it.visiovoice.api.dto.ErrorResponse;
import it.visiovoice.api.dto.PerceiveRequest;
import it.visiovoice.api.dto.PerceiveResponse;
import it.visiovoice.model.DetailLevel;
import it.visiovoice.model.FidelityReport;
import it.visiovoice.model.Phase;
import it.visiovoice.model.Scenario;
import it.visiovoice.model.ScreenModel;
import it.visiovoice.model.SpokenScript;
import java.util.List;
import java.util.ArrayList;
import it.visiovoice.model.VisualKind;
import it.visiovoice.model.VisualDescription;
import it.visiovoice.model.VisualAsset;
import it.visiovoice.orchestrator.ScenarioStore;
import it.visiovoice.orchestrator.ScriptStore;
import it.visiovoice.orchestrator.SessionState;
import it.visiovoice.orchestrator.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Il canale che <b>racconta</b>: riceve la pagina, restituisce cosa c'e' e come dirlo.
 *
 * <p>Gli strati di percezione e narrazione arrivano come {@link ObjectProvider} e non come
 * dipendenza obbligatoria. E' deliberato: quattro agenti lavorano in parallelo su questi
 * package, e l'applicazione deve avviarsi e rispondere anche prima che siano pronti. Un
 * contesto Spring che non parte perche' manca un bean blocca tutti e quattro insieme.
 */
@RestController
@RequestMapping("/api")
public class PerceiveController {

    private static final Logger LOG = LoggerFactory.getLogger(PerceiveController.class);

    private final ObjectProvider<PerceptionPort> perception;
    private final ObjectProvider<NarrationPort> narration;
    private final SessionStore sessions;
    private final ScenarioStore scenarios;
    private final ScriptStore scripts;

    public PerceiveController(ObjectProvider<PerceptionPort> perception,
                              ObjectProvider<NarrationPort> narration,
                              SessionStore sessions,
                              ScenarioStore scenarios,
                              ScriptStore scripts) {
        this.perception = perception;
        this.narration = narration;
        this.sessions = sessions;
        this.scenarios = scenarios;
        this.scripts = scripts;
    }

    @PostMapping("/perceive")
    public ResponseEntity<Object> perceive(@RequestBody PerceiveRequest request) {
        PerceptionPort reader = perception.getIfAvailable();
        if (reader == null) {
            return notReady("percezione");
        }
        ScreenModel screen = reader.perceive(request.html(), request.url());
        DetailLevel level = request.level() == null ? DetailLevel.STANDARD : request.level();
        SpokenScript script = null;
        FidelityReport report = null;
        List<VisualDescription> visuals = List.of();
        NarrationPort teller = narration.getIfAvailable();
        if (teller != null) {
            AgentContext ctx = contextFor(request.sessionId(), level);
            script = teller.narrate(screen, level, ctx);
            if (script != null) {
                scripts.remember(script);
                report = scripts.report(script.id()).orElse(null);
            }
            visuals = describeInformativeVisuals(screen, teller, ctx);
        }
        sessions.update(request.sessionId(), state -> {
            SessionState moved = state.withPhase(Phase.NARRATE).withScreen(screen.screenId());
            return screen.hasSteps() ? moved.withStep(screen.stepIndex()) : moved;
        });
        return ResponseEntity.ok(new PerceiveResponse(request.sessionId(), screen, script, report, visuals));
    }

    @GetMapping("/narrate/{scriptId}")
    public ResponseEntity<Object> narrate(@PathVariable String scriptId,
                                          @RequestParam(required = false) DetailLevel level) {
        return scripts.script(scriptId)
                .<ResponseEntity<Object>>map(script -> ResponseEntity.ok(new PerceiveResponse(
                        null, null, script, scripts.report(scriptId).orElse(null))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                        "racconto-assente",
                        "Non ho piu' il racconto di questa schermata. Rileggila e riprova.")));
    }

    private AgentContext contextFor(String sessionId, DetailLevel level) {
        Scenario scenario = sessions.find(sessionId)
                .flatMap(state -> scenarios.find(state.scenarioId()))
                .or(scenarios::defaultScenario)
                .orElse(null);
        return new AgentContext(sessionId == null ? "senza-sessione" : sessionId,
                scenario, level, java.util.Map.of());
    }

    private static ResponseEntity<Object> notReady(String layer) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.of(
                "strato-non-pronto",
                "Lo strato di " + layer + " non e' ancora attivo su questo server, quindi non "
                + "posso raccontarti questa schermata. Meglio dirtelo che inventare."));
    }

    /**
     * Chiede la descrizione dei soli asset che portano informazione.
     *
     * <p>Un'icona o un'immagine decorativa non ha nulla da raccontare: descriverla
     * riempirebbe di rumore l'ascolto di chi naviga a 380 parole al minuto, e costerebbe una
     * chiamata al modello per niente. Passano solo tabelle, grafici e indicatori di passo.
     *
     * <p>Se la descrizione di un asset fallisce, gli altri proseguono: perdere la tabella
     * degli importi perche' un'icona ha dato errore sarebbe il peggiore dei baratti.
     */
    private List<VisualDescription> describeInformativeVisuals(ScreenModel screen,
                                                               NarrationPort teller,
                                                               AgentContext ctx) {
        List<VisualDescription> descritti = new ArrayList<>();
        for (VisualAsset asset : screen.visuals()) {
            if (!portaInformazione(asset.kind())) {
                continue;
            }
            try {
                VisualDescription description = teller.describeVisual(asset, ctx);
                if (description != null) {
                    descritti.add(description);
                }
            } catch (RuntimeException e) {
                LOG.warn("descrizione non riuscita per l'asset {}: {}", asset.id(), e.toString());
            }
        }
        return descritti;
    }

    private static boolean portaInformazione(VisualKind kind) {
        return kind == VisualKind.DATA_TABLE
                || kind == VisualKind.CHART
                || kind == VisualKind.STEP_INDICATOR;
    }

}
