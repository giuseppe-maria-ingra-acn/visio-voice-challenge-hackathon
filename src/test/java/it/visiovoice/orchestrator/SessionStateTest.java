package it.visiovoice.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.visiovoice.model.DetailLevel;
import it.visiovoice.model.Phase;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lo stato di sessione evolve per copia, e non contiene i dati dell'utente. */
class SessionStateTest {

    private static SessionState fresh() {
        return SessionState.open("vv-test", "inps-assegno-unico", DetailLevel.STANDARD, Instant.EPOCH);
    }

    @Test
    @DisplayName("cambiare fase produce un nuovo stato e lascia intatto il precedente")
    void evolvePerCopia() {
        SessionState before = fresh();

        SessionState after = before.withPhase(Phase.GUIDE).withStep(4).withNarrated("seg-14");

        assertNotSame(before, after);
        assertEquals(Phase.INTRO, before.phase());
        assertEquals(Phase.GUIDE, after.phase());
        assertEquals(4, after.currentStep());
        assertTrue(after.hasNarrated("seg-14"));
        assertTrue(before.narratedSegmentIds().isEmpty(),
                "lo stato che un agente sta leggendo non deve poter cambiare sotto di lui");
    }

    @Test
    @DisplayName("la lista dei segmenti narrati e' immutabile dall'esterno")
    void leListeSonoCopiate() {
        SessionState state = fresh().withNarrated("seg-1");

        assertThrows(UnsupportedOperationException.class, () -> state.narratedSegmentIds().add("seg-2"));
    }

    @Test
    @DisplayName("l'ultimo segmento narrato e' recuperabile: e' cio' a cui si riferisce il comando P")
    void ultimoSegmentoNarrato() {
        SessionState state = fresh().withNarrated("seg-1").withNarrated("seg-2");

        assertEquals("seg-2", state.lastNarratedSegmentId().orElseThrow());
    }

    @Test
    @DisplayName("lo stato non espone nessun valore di campo: la pagina e' la fonte di verita'")
    void nessunValoreDiCampoNelloStato() {
        List<String> componentNames = java.util.Arrays.stream(SessionState.class.getRecordComponents())
                .map(c -> c.getName().toLowerCase())
                .toList();

        assertTrue(componentNames.stream().noneMatch(n -> n.contains("value") || n.contains("answer")
                        || n.contains("field") || n.contains("input")),
                "se lo stato tenesse una copia dei dati, potremmo ritrasmetterli diversi da come "
                + "l'utente li ha scritti, e lui non avrebbe modo di accorgersene: " + componentNames);
    }

    @Test
    @DisplayName("un trace si accoda allo stato, cosi' la mappa del contributo AI si genera da qui")
    void iTraceSiAccodano() {
        AgentTrace trace = AgentTrace.success("tr-1", "narration", "vv-test", Instant.EPOCH,
                12L, "ScreenModel", "SpokenScript", it.visiovoice.model.Provenance.AI_REPHRASED);

        SessionState state = fresh().withTrace(trace);

        assertEquals(1, state.traces().size());
        assertTrue(state.traces().get(0).describe().contains("narration"));
    }
}
