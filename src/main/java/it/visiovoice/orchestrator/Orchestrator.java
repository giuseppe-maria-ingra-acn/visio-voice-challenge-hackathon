package it.visiovoice.orchestrator;

import it.visiovoice.agents.Agent;
import it.visiovoice.agents.AgentContext;
import it.visiovoice.model.Provenance;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * Invoca gli agenti e registra cosa hanno fatto.
 *
 * <p>Un ciclo, non uno switch: {@link Agent} ha una firma sola, quindi qui non c'e' un ramo per
 * agente e aggiungerne uno non tocca questa classe. Il valore non e' l'eleganza: e' che la
 * misurazione dei tempi e la registrazione dei passaggi stanno in un punto solo, e quindi
 * nessun agente puo' dimenticarsi di essere tracciato.
 */
@Service
public class Orchestrator {

    private final SessionStore sessions;
    private final Clock clock;

    public Orchestrator(SessionStore sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    /**
     * Esegue un agente, ne misura il tempo e appende il trace alla sessione.
     *
     * @param provenanceOf come si ricava la provenance dall'uscita: e' cio' che rende la mappa
     *                     del contributo AI generabile invece che da scrivere a mano
     */
    public <I, O> O run(Agent<I, O> agent, I input, AgentContext ctx,
                        Function<O, Provenance> provenanceOf) {
        Instant started = clock.instant();
        long startNanos = System.nanoTime();
        String traceId = "tr-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            O output = agent.run(input, ctx);
            long millis = (System.nanoTime() - startNanos) / 1_000_000L;
            Provenance provenance = output == null || provenanceOf == null
                    ? Provenance.AI_INFERRED
                    : provenanceOf.apply(output);
            record(ctx, AgentTrace.success(traceId, agent.name(), ctx.sessionId(), started, millis,
                    summarize(input), summarize(output), provenance));
            return output;
        } catch (RuntimeException e) {
            // Un agente che si rompe non deve spegnere la sessione: il trace lo registra, chi
            // chiama degrada. Un'eccezione che arriva all'interfaccia diventa una frase che una
            // sintesi vocale legge all'utente, e non e' un messaggio: e' un vicolo cieco.
            long millis = (System.nanoTime() - startNanos) / 1_000_000L;
            record(ctx, AgentTrace.failure(traceId, agent.name(), ctx.sessionId(), started, millis,
                    summarize(input), e.getClass().getSimpleName() + ": " + e.getMessage()));
            throw e;
        }
    }

    /** Come sopra, quando la provenance dell'uscita non e' ricavabile da un campo. */
    public <I, O> O run(Agent<I, O> agent, I input, AgentContext ctx) {
        return run(agent, input, ctx, output -> Provenance.AI_INFERRED);
    }

    private void record(AgentContext ctx, AgentTrace trace) {
        sessions.update(ctx.sessionId(), state -> state.withTrace(trace));
    }

    /** Riassunto breve: un trace che contiene l'HTML intero non si legge. */
    private static String summarize(Object value) {
        if (value == null) {
            return "null";
        }
        String text = value instanceof CharSequence ? value.toString() : value.getClass().getSimpleName();
        return text.length() <= 120 ? text : text.substring(0, 117) + "...";
    }
}
