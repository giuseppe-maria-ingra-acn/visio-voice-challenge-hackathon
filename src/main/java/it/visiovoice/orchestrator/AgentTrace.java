package it.visiovoice.orchestrator;

import it.visiovoice.model.Provenance;
import java.time.Instant;
import java.util.Objects;

/**
 * Cosa ha fatto un agente, una volta: quale input, quale uscita, quanto ci ha messo.
 *
 * <p>Non e' logging. Da questi trace si genera {@code docs/AI-CONTRIBUTION.md}: la mappa di
 * dove ha lavorato l'AI non si scrive a memoria la sera prima della consegna, si legge da qui.
 * Per questo {@code outputProvenance} e' un campo e non una nota: e' la colonna che dice, per
 * ogni pezzo di prodotto, se l'ha letto dalla pagina o l'ha dedotto.
 *
 * @param inputSummary  descrizione breve dell'input, non l'input intero: un trace che contiene
 *                      tutto l'HTML non si legge, e i valori dei campi non vanno duplicati
 * @param outputSummary descrizione breve dell'uscita
 * @param ok            false se l'agente non e' riuscito a produrre nulla di utile
 */
public record AgentTrace(
        String id,
        String agentName,
        String sessionId,
        Instant startedAt,
        long durationMs,
        String inputSummary,
        String outputSummary,
        Provenance outputProvenance,
        boolean ok,
        String note) {

    public AgentTrace {
        Objects.requireNonNull(agentName, "agentName");
        startedAt = startedAt == null ? Instant.EPOCH : startedAt;
    }

    public static AgentTrace success(String id, String agentName, String sessionId, Instant startedAt,
                                     long durationMs, String inputSummary, String outputSummary,
                                     Provenance outputProvenance) {
        return new AgentTrace(id, agentName, sessionId, startedAt, durationMs,
                inputSummary, outputSummary, outputProvenance, true, null);
    }

    public static AgentTrace failure(String id, String agentName, String sessionId, Instant startedAt,
                                     long durationMs, String inputSummary, String note) {
        return new AgentTrace(id, agentName, sessionId, startedAt, durationMs,
                inputSummary, null, Provenance.AI_INFERRED, false, note);
    }

    /** Riga della mappa del contributo AI. */
    public String describe() {
        String provenanceLabel = outputProvenance == null ? "non dichiarata" : outputProvenance.italianLabel();
        return agentName + " | " + durationMs + " ms | " + provenanceLabel
                + (ok ? "" : " | NON RIUSCITO: " + note);
    }
}
