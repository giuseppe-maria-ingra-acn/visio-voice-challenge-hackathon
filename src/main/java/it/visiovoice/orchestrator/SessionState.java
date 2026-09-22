package it.visiovoice.orchestrator;

import it.visiovoice.model.DetailLevel;
import it.visiovoice.model.Phase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dove siamo nel percorso dell'utente. Immutabile: evolve per copia.
 *
 * <h2>Cosa non c'e' qui, e perche'</h2>
 * Nessun valore di campo. I dati dell'utente stanno nel DOM della pagina, che e' la fonte di
 * verita': VisioVoice e' un livello che ripara quella pagina, non un'applicazione a fianco in
 * cui ridigitare le cose. Se tenessimo una copia dei valori, diventeremmo un intermediario che
 * puo' ritrasmettere un dato diverso da quello che l'utente ha scritto, e lui - che non vede lo
 * schermo - non avrebbe modo di accorgersene. Quando serve validare, la pagina viene letta in
 * quell'istante e il valore arriva come parametro.
 *
 * <p>Immutabile e non sincronizzato: {@link AgentTrace} puo' conservare gli stati intermedi, e
 * nessun agente puo' modificare lo stato che un altro sta leggendo.
 *
 * @param narratedSegmentIds cosa l'utente ha gia' sentito, in ordine. Serve a non ripetersi e a
 *                           dare un senso a "da dove viene l'ultima frase?"
 */
public record SessionState(
        String sessionId,
        String scenarioId,
        Phase phase,
        int currentStep,
        String currentScreenId,
        DetailLevel level,
        List<String> narratedSegmentIds,
        List<AgentTrace> traces,
        Instant openedAt) {

    public SessionState {
        Objects.requireNonNull(sessionId, "sessionId");
        phase = phase == null ? Phase.INTRO : phase;
        level = level == null ? DetailLevel.STANDARD : level;
        narratedSegmentIds = List.copyOf(narratedSegmentIds == null ? List.of() : narratedSegmentIds);
        traces = List.copyOf(traces == null ? List.of() : traces);
        openedAt = openedAt == null ? Instant.EPOCH : openedAt;
    }

    public static SessionState open(String sessionId, String scenarioId, DetailLevel level, Instant now) {
        return new SessionState(sessionId, scenarioId, Phase.INTRO, 0, null, level,
                List.of(), List.of(), now);
    }

    public SessionState withPhase(Phase newPhase) {
        return new SessionState(sessionId, scenarioId, newPhase, currentStep, currentScreenId,
                level, narratedSegmentIds, traces, openedAt);
    }

    public SessionState withStep(int step) {
        return new SessionState(sessionId, scenarioId, phase, step, currentScreenId,
                level, narratedSegmentIds, traces, openedAt);
    }

    public SessionState withScreen(String screenId) {
        return new SessionState(sessionId, scenarioId, phase, currentStep, screenId,
                level, narratedSegmentIds, traces, openedAt);
    }

    public SessionState withLevel(DetailLevel newLevel) {
        return new SessionState(sessionId, scenarioId, phase, currentStep, currentScreenId,
                newLevel, narratedSegmentIds, traces, openedAt);
    }

    /** Segna un segmento come gia' pronunciato. Se c'era gia', non lo duplica. */
    public SessionState withNarrated(String segmentId) {
        if (segmentId == null || narratedSegmentIds.contains(segmentId)) {
            return this;
        }
        List<String> merged = new ArrayList<>(narratedSegmentIds);
        merged.add(segmentId);
        return new SessionState(sessionId, scenarioId, phase, currentStep, currentScreenId,
                level, merged, traces, openedAt);
    }

    public SessionState withTrace(AgentTrace trace) {
        if (trace == null) {
            return this;
        }
        List<AgentTrace> merged = new ArrayList<>(traces);
        merged.add(trace);
        return new SessionState(sessionId, scenarioId, phase, currentStep, currentScreenId,
                level, narratedSegmentIds, merged, openedAt);
    }

    /** L'ultimo segmento pronunciato, se ce n'e' uno: e' cio' a cui si riferisce il comando P. */
    public java.util.Optional<String> lastNarratedSegmentId() {
        return narratedSegmentIds.isEmpty()
                ? java.util.Optional.empty()
                : java.util.Optional.of(narratedSegmentIds.get(narratedSegmentIds.size() - 1));
    }

    public boolean hasNarrated(String segmentId) {
        return narratedSegmentIds.contains(segmentId);
    }
}
