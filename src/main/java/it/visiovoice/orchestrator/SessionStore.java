package it.visiovoice.orchestrator;

import it.visiovoice.model.DetailLevel;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/**
 * Le sessioni aperte, in memoria.
 *
 * <p>In memoria e senza persistenza: una sessione vale il tempo di una procedura, e un database
 * qui aggiungerebbe un modo di rompersi senza aggiungere nulla che l'utente possa usare. Non
 * contiene i dati dell'utente - {@link SessionState} non li ha - quindi non c'e' nemmeno
 * qualcosa da proteggere fra una sessione e l'altra.
 */
@Component
public class SessionStore {

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    public SessionStore(Clock clock) {
        this.clock = clock;
    }

    /** Apre una sessione nuova su uno scenario. */
    public SessionState open(String scenarioId, DetailLevel level) {
        SessionState state = SessionState.open(
                "vv-" + UUID.randomUUID().toString().substring(0, 8),
                scenarioId, level, clock.instant());
        sessions.put(state.sessionId(), state);
        return state;
    }

    public Optional<SessionState> find(String sessionId) {
        return sessionId == null ? Optional.empty() : Optional.ofNullable(sessions.get(sessionId));
    }

    public SessionState save(SessionState state) {
        sessions.put(state.sessionId(), state);
        return state;
    }

    /**
     * Applica una trasformazione allo stato e la registra, in un colpo solo.
     *
     * <p>Atomica, perche' due richieste dell'estensione possono arrivare insieme e uno stato
     * immutabile aggiornato con un {@code get} seguito da un {@code put} perde un
     * aggiornamento. Immutabile non vuol dire al sicuro dalla concorrenza: vuol dire che il
     * punto in cui stare attenti e' uno solo, ed e' questo.
     */
    public Optional<SessionState> update(String sessionId, UnaryOperator<SessionState> change) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.computeIfPresent(sessionId, (id, state) -> change.apply(state)));
    }

    public int count() {
        return sessions.size();
    }
}
