package it.visiovoice.api;

import it.visiovoice.api.dto.ErrorResponse;
import it.visiovoice.api.dto.SessionRequest;
import it.visiovoice.api.dto.SessionResponse;
import it.visiovoice.model.DetailLevel;
import it.visiovoice.model.Scenario;
import it.visiovoice.orchestrator.ScenarioStore;
import it.visiovoice.orchestrator.SessionState;
import it.visiovoice.orchestrator.SessionStore;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Apre e legge le sessioni. Una sessione non contiene i dati dell'utente: vedi {@link SessionState}. */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final SessionStore sessions;
    private final ScenarioStore scenarios;

    public SessionController(SessionStore sessions, ScenarioStore scenarios) {
        this.sessions = sessions;
        this.scenarios = scenarios;
    }

    @PostMapping
    public ResponseEntity<Object> open(@RequestBody(required = false) SessionRequest request) {
        String requestedId = request == null ? null : request.scenarioId();
        DetailLevel level = request == null || request.level() == null
                ? DetailLevel.STANDARD
                : request.level();
        Optional<Scenario> scenario = requestedId == null
                ? scenarios.defaultScenario()
                : scenarios.find(requestedId);
        if (scenario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                    "scenario-assente",
                    "Non ho una scheda per questo servizio, quindi non posso accompagnarti senza "
                    + "inventare. Prova con il servizio predefinito."));
        }
        SessionState state = sessions.open(scenario.get().id(), level);
        return ResponseEntity.ok(toResponse(state, scenario.get()));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<Object> read(@PathVariable String sessionId) {
        Optional<SessionState> state = sessions.find(sessionId);
        if (state.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                    "sessione-assente",
                    "Questa sessione non e' piu' attiva. Ricomincia dalla pagina del servizio."));
        }
        Scenario scenario = scenarios.find(state.get().scenarioId()).orElse(null);
        return ResponseEntity.ok(toResponse(state.get(), scenario));
    }

    private static SessionResponse toResponse(SessionState state, Scenario scenario) {
        return new SessionResponse(
                state.sessionId(),
                state.scenarioId(),
                scenario == null ? null : scenario.goal(),
                state.phase(),
                state.currentStep(),
                scenario == null ? 0 : scenario.procedure().size(),
                state.level());
    }
}
