package it.visiovoice.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.visiovoice.model.BarrierType;
import it.visiovoice.model.Provenance;
import it.visiovoice.model.Scenario;
import it.visiovoice.model.ScenarioField;
import it.visiovoice.model.ScenarioStep;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo scenario vero deve caricarsi nei tipi veri, senza che nessuno lo modifichi.
 *
 * <p>I numeri attesi vengono contati a mano su {@code inps-assegno-unico.json}: se qualcuno
 * arricchisce lo scenario i conteggi vanno aggiornati, ed e' voluto. Un test che si limita a
 * dire "si e' caricato" passa anche quando meta' del file e' stata ignorata in silenzio, che e'
 * esattamente il modo in cui un campo scritto e mai letto sparisce senza che nessuno se ne
 * accorga.
 */
class ScenarioLoadTest {

    private static Scenario scenario() {
        return new ScenarioStore().find("inps-assegno-unico").orElseThrow();
    }

    @Test
    @DisplayName("lo scenario si carica senza errori di deserializzazione")
    void loScenarioSiCarica() {
        ScenarioStore store = new ScenarioStore();

        assertTrue(store.loadErrors().isEmpty(), () -> "errori di caricamento: " + store.loadErrors());
        assertTrue(store.find("inps-assegno-unico").isPresent());
    }

    @Test
    @DisplayName("le cinque barriere si caricano su quattro tipi distinti")
    void cinqueBarriereSuQuattroTipi() {
        Scenario scenario = scenario();

        assertEquals(5, scenario.barriers().size());
        assertEquals(4, scenario.barriers().stream().map(b -> b.type()).distinct().count());
        assertFalse(scenario.barriers().stream().anyMatch(b -> b.type() == BarrierType.UNKNOWN),
                "un tipo non riconosciuto vuol dire che l'enum e lo scenario si sono separati");
    }

    @Test
    @DisplayName("i dieci fatti citati si caricano e hanno una provenance dichiarata")
    void dieciFattiConProvenance() {
        Scenario scenario = scenario();

        assertEquals(10, scenario.sourceFacts().size());
        assertTrue(scenario.sourceFacts().stream().allMatch(f -> f.provenance() != null));
    }

    @Test
    @DisplayName("i sei passi e i quindici campi si caricano tutti")
    void seiPassiEQuindiciCampi() {
        Scenario scenario = scenario();

        assertEquals(6, scenario.procedure().size());
        assertEquals(15, scenario.procedure().stream().mapToInt(s -> s.fields().size()).sum());
    }

    @Test
    @DisplayName("il campo IBAN dichiara entrambe le barriere che lo rendono inutilizzabile")
    void leBarriereDelCampoSonoUnaLista() {
        Scenario scenario = scenario();
        ScenarioField iban = scenario.step(4).orElseThrow().field("iban").orElseThrow();

        // Due barriere distinte sullo stesso campo, e servono entrambe:
        //   b2 - non ha nome accessibile, quindi Marco non sa cosa scriverci
        //   b5 - l'errore e' segnalato dal solo colore, quindi non sa perche' e' stato rifiutato
        // Ripararne una sola lascia il campo inservibile: sapere cosa scrivere senza sapere
        // di aver sbagliato, o saperlo senza sapere cosa scrivere.
        assertEquals(List.of("b2", "b5"), iban.barrierIds(),
                "il campo IBAN attiva due barriere: dichiararne una sola le nasconde al codice");
        assertTrue(scenario.barrier("b2").isPresent());
        assertTrue(scenario.barrier("b5").isPresent());
    }

    @Test
    @DisplayName("l'etichetta di un campo senza label sulla pagina diventa dedotta, non letta")
    void lEtichettaDiUnCampoSenzaLabelEDedotta() {
        Scenario scenario = scenario();
        ScenarioField iban = scenario.step(4).orElseThrow().field("iban").orElseThrow();

        assertEquals(Provenance.AI_INFERRED,
                iban.asFormField(Provenance.AI_INFERRED).labelProvenance(),
                "dire il nome di un campo che la pagina non nomina e' una nostra deduzione");
    }

    @Test
    @DisplayName("i valori calcolati non entrano nel corpus di ancoraggio")
    void ilCorpusContieneSoloIFattiCitati() {
        Scenario scenario = scenario();
        String corpus = scenario.sourceCorpus();

        assertTrue(corpus.contains("203,80"), "i fatti citati devono essere ancorabili");
        assertTrue(scenario.computedFacts().stream()
                .allMatch(c -> !corpus.contains(c.text())),
                "un valore calcolato nel corpus ancorerebbe se stesso");
    }

    @Test
    @DisplayName("ogni passo dichiara un titolo pronunciabile")
    void ogniPassoHaUnTitolo() {
        for (ScenarioStep step : scenario().procedure()) {
            assertFalse(step.title() == null || step.title().isBlank(),
                    "il passo " + step.step() + " non ha un titolo da leggere");
        }
    }
}
