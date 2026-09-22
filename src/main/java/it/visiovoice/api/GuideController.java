package it.visiovoice.api;

import it.visiovoice.agents.ProcedurePort;
import it.visiovoice.agents.ValidationPort;
import it.visiovoice.api.dto.ErrorResponse;
import it.visiovoice.api.dto.ReviewRequest;
import it.visiovoice.api.dto.ValidateRequest;
import it.visiovoice.model.FieldKind;
import it.visiovoice.model.FormField;
import it.visiovoice.model.Phase;
import it.visiovoice.model.Provenance;
import it.visiovoice.model.ProcedurePlan;
import it.visiovoice.model.Scenario;
import it.visiovoice.model.ScenarioField;
import it.visiovoice.model.SpokenScript;
import it.visiovoice.model.ValidationResult;
import it.visiovoice.orchestrator.ScenarioStore;
import it.visiovoice.orchestrator.ScriptStore;
import it.visiovoice.orchestrator.SessionStore;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Il canale che <b>accompagna</b>: giudica un valore, riepiloga prima dell'invio.
 *
 * <p>Entrambi gli endpoint ricevono i valori nella richiesta, letti dal DOM in quell'istante.
 * Il backend non ne conserva copia: la pagina e' la fonte di verita' e noi siamo il livello che
 * la ripara, non un'applicazione a fianco che la ricopia.
 */
@RestController
@RequestMapping("/api/guide")
public class GuideController {

    private final ObjectProvider<ValidationPort> validation;
    private final ObjectProvider<ProcedurePort> procedure;
    private final SessionStore sessions;
    private final ScenarioStore scenarios;
    private final ScriptStore scripts;

    public GuideController(ObjectProvider<ValidationPort> validation,
                           ObjectProvider<ProcedurePort> procedure,
                           SessionStore sessions,
                           ScenarioStore scenarios,
                           ScriptStore scripts) {
        this.validation = validation;
        this.procedure = procedure;
        this.sessions = sessions;
        this.scenarios = scenarios;
        this.scripts = scripts;
    }

    /** Un campo alla volta: l'estensione manda cio' che ha letto, riceve una frase da annunciare. */
    @PostMapping("/validate")
    public ResponseEntity<Object> validate(@RequestBody ValidateRequest request) {
        ValidationPort validator = validation.getIfAvailable();
        if (validator == null) {
            return notReady("controllo dei dati");
        }
        FormField field = resolveField(request.sessionId(), request.fieldId());
        if (field == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                    "campo-sconosciuto",
                    "Non conosco questo campo, quindi non posso dirti se il valore va bene. "
                    + "Preferisco dirtelo piuttosto che darti una conferma che non ho verificato."));
        }
        ValidationResult result = validator.validate(field, request.value());
        return ResponseEntity.ok(result);
    }

    /** Il riepilogo parlato: i valori arrivano dal DOM, non dallo stato di sessione. */
    @PostMapping("/review")
    public ResponseEntity<Object> review(@RequestBody ReviewRequest request) {
        ProcedurePort coach = procedure.getIfAvailable();
        if (coach == null) {
            return notReady("guida alla procedura");
        }
        Optional<Scenario> scenario = sessions.find(request.sessionId())
                .flatMap(state -> scenarios.find(state.scenarioId()))
                .or(scenarios::defaultScenario);
        if (scenario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                    "scenario-assente",
                    "Non ho la scheda di questo servizio, quindi non posso rileggerti la domanda "
                    + "prima dell'invio."));
        }
        ProcedurePlan plan = coach.plan(null, scenario.get());
        Map<String, String> values = request.values() == null ? Map.of() : request.values();
        SpokenScript summary = coach.reviewBeforeSubmit(plan, values);
        if (summary != null) {
            scripts.remember(summary);
        }
        sessions.update(request.sessionId(), state -> state.withPhase(Phase.REVIEW));
        return ResponseEntity.ok(summary);
    }

    /**
     * Il campo, ricavato dallo scenario della sessione.
     *
     * <p>L'etichetta dello scenario viene marcata {@link Provenance#AI_INFERRED} quando lo
     * scenario dichiara che quel campo e' colpito da una barriera di etichetta mancante: in quel
     * caso il nome del campo non sta nella pagina e glielo stiamo dando noi. Dirlo qui, una
     * volta, evita che ogni chiamante lo decida a modo proprio.
     */
    private FormField resolveField(String sessionId, String fieldId) {
        if (fieldId == null) {
            return null;
        }
        Optional<Scenario> scenario = sessions.find(sessionId)
                .flatMap(state -> scenarios.find(state.scenarioId()))
                .or(scenarios::defaultScenario);
        if (scenario.isEmpty()) {
            return null;
        }
        for (var step : scenario.get().procedure()) {
            Optional<ScenarioField> found = step.field(fieldId);
            if (found.isPresent()) {
                ScenarioField declared = found.get();
                boolean labelMissingOnPage = declared.barrierIds().stream()
                        .flatMap(id -> scenario.get().barrier(id).stream())
                        .anyMatch(b -> b.type() == it.visiovoice.model.BarrierType.UNLABELED_INPUT);
                return declared.asFormField(labelMissingOnPage
                        ? Provenance.AI_INFERRED
                        : Provenance.SOURCE_VERBATIM);
            }
        }
        return new FormField(fieldId, fieldId, Provenance.AI_INFERRED, FieldKind.OTHER,
                null, null, false, java.util.List.of(), null);
    }

    private static ResponseEntity<Object> notReady(String layer) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.of(
                "strato-non-pronto",
                "Lo strato di " + layer + " non e' ancora attivo su questo server. Non posso "
                + "confermarti niente che non abbia verificato."));
    }
}
