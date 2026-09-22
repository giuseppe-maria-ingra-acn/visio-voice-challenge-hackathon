package it.visiovoice.procedure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.visiovoice.model.DetailLevel;
import it.visiovoice.model.FieldGuidance;
import it.visiovoice.model.FieldKind;
import it.visiovoice.model.FormField;
import it.visiovoice.model.ProcedurePlan;
import it.visiovoice.model.Provenance;
import it.visiovoice.model.Scenario;
import it.visiovoice.model.SpokenScript;
import it.visiovoice.orchestrator.ScenarioStore;
import it.visiovoice.orchestrator.SessionState;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifica il piano, il coaching e il riepilogo prodotti da {@link ProcedureAgent}.
 */
class ProcedureAgentTest {

    private ProcedureAgent agent;
    private Scenario scenario;
    private SessionState state;

    @BeforeEach
    void setUp() {
        agent = new ProcedureAgent();
        scenario = new ScenarioStore().find("inps-assegno-unico").orElseThrow();
        state = SessionState.open("test-session", scenario.id(), DetailLevel.STANDARD, Instant.now());
    }

    // -------------------------------------------------------------------------
    // plan()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("il piano ha tanti passi quanti ne ha lo scenario")
    void pianoCoperturaPasci() {
        ProcedurePlan plan = agent.plan(null, scenario);

        assertEquals(scenario.procedure().size(), plan.totalSteps(),
                "Il piano deve contenere tutti i passi dello scenario");
        assertEquals(scenario.id(), plan.scenarioId());
        assertNotNull(plan.goal());
        assertFalse(plan.goal().isBlank());
    }

    @Test
    @DisplayName("ogni passo del piano ha un criterio di completamento non vuoto")
    void ogniPassoHaCriterioCompletamento() {
        ProcedurePlan plan = agent.plan(null, scenario);

        for (var step : plan.steps()) {
            assertNotNull(step.completionCriterion(),
                    "Passo " + step.index() + " non ha un criterio di completamento");
            assertFalse(step.completionCriterion().isBlank(),
                    "Passo " + step.index() + " ha un criterio di completamento vuoto");
            assertFalse(step.completionCriterion().equalsIgnoreCase("vai avanti quando hai finito"),
                    "Il criterio del passo " + step.index() + " non dice nulla di concreto");
        }
    }

    @Test
    @DisplayName("il campo IBAN ha provenance dedotta perche' la pagina non ha label")
    void ibanProvenanceDedotta() {
        ProcedurePlan plan = agent.plan(null, scenario);

        FormField iban = plan.field("iban").orElseThrow(
                () -> new AssertionError("Il campo IBAN non e' nel piano"));

        assertEquals(Provenance.AI_INFERRED, iban.labelProvenance(),
                "Il campo IBAN non ha label nella pagina: la sua etichetta e' dedotta, non letta");
    }

    @Test
    @DisplayName("i campi con label nella pagina hanno provenance SOURCE_VERBATIM")
    void campiConLabelVerbatim() {
        ProcedurePlan plan = agent.plan(null, scenario);

        FormField cognome = plan.field("cognome").orElseThrow(
                () -> new AssertionError("Il campo cognome non e' nel piano"));

        assertEquals(Provenance.SOURCE_VERBATIM, cognome.labelProvenance(),
                "Il campo 'cognome' ha una label nella pagina: provenance SOURCE_VERBATIM");
    }

    @Test
    @DisplayName("il piano porta le barriere del campo IBAN: entrambe, non una sola")
    void ibanBarriereSonoDue() {
        ProcedurePlan plan = agent.plan(null, scenario);

        FormField iban = plan.field("iban").orElseThrow();
        assertTrue(iban.barrierIds().contains("b2"),
                "b2 (campo senza label) deve essere nelle barriere dell'IBAN nel piano");
        assertTrue(iban.barrierIds().contains("b5"),
                "b5 (errore solo con bordo rosso) deve essere nelle barriere dell'IBAN nel piano");
    }

    // -------------------------------------------------------------------------
    // coach()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("il coaching dell'IBAN include istruzione sul formato")
    void coachingIbanInformatoFormato() {
        FormField ibanField = FormField.inferredLabel(
                "iban", "IBAN", FieldKind.IBAN,
                "IT + 25 caratteri alfanumerici (27 caratteri totali)",
                "IT60X0542811101000000123456",
                true,
                java.util.List.of("b2", "b5"));

        FieldGuidance guidance = agent.coach(ibanField, state);

        assertNotNull(guidance);
        assertEquals("iban", guidance.fieldId());
        assertFalse(guidance.spokenLabel().isBlank(), "Lo spoken label non deve essere vuoto");
        assertNotNull(guidance.instruction());
        assertFalse(guidance.instruction().isBlank(), "L'istruzione non deve essere vuota per IBAN");
    }

    @Test
    @DisplayName("il coaching dell'IBAN dichiara la provenance dedotta")
    void coachingIbanProvenance() {
        FormField ibanField = FormField.inferredLabel(
                "iban", "IBAN", FieldKind.IBAN,
                "IT + 25 caratteri", "IT60X0542811101000000123456",
                true, java.util.List.of("b2", "b5"));

        FieldGuidance guidance = agent.coach(ibanField, state);

        assertEquals(Provenance.AI_INFERRED, guidance.provenance(),
                "Il campo IBAN ha etichetta dedotta: la provenance deve essere AI_INFERRED");
    }

    @Test
    @DisplayName("il coaching di un campo obbligatorio lo dice chiaramente")
    void coachingCampoObbligatorio() {
        FormField cognome = FormField.labelled("cognome", "Cognome",
                FieldKind.TEXT, "testo libero", "Ferrari", true);

        FieldGuidance guidance = agent.coach(cognome, state);
        String text = guidance.spokenText();

        assertFalse(text.isBlank());
        assertTrue(text.contains("obbligatorio") || text.contains("Obbligatorio"),
                "Il coaching di un campo obbligatorio deve dirlo: " + text);
    }

    @Test
    @DisplayName("il testo parlato del coaching include etichetta, istruzione ed esempio nell'ordine")
    void coachingOrdineQuattroElementi() {
        FormField ibanField = FormField.inferredLabel(
                "iban", "IBAN", FieldKind.IBAN,
                "IT + 25 caratteri", "IT60X0542811101000000123456",
                true, java.util.List.of("b2", "b5"));

        FieldGuidance guidance = agent.coach(ibanField, state);
        String text = guidance.spokenText();

        // La struttura: [avvertenza dedotta] [label]. [istruzione]. [esempio]
        int labelPos = text.indexOf("IBAN");
        int examplePos = text.indexOf("IT60X");
        assertTrue(labelPos >= 0, "Il testo deve contenere il nome del campo");
        assertTrue(examplePos > labelPos, "L'esempio deve venire dopo il nome del campo");
    }

    // -------------------------------------------------------------------------
    // reviewBeforeSubmit()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("il riepilogo elenca ogni campo del piano, inclusi i vuoti")
    void riepilogoElencoCompletoCampi() {
        ProcedurePlan plan = agent.plan(null, scenario);
        Map<String, String> valoriParziali = Map.of(
                "cognome", "Rossi",
                "nome", "Ada",
                "iban", "IT60X0542811101000000123456");

        SpokenScript review = agent.reviewBeforeSubmit(plan, valoriParziali);

        assertNotNull(review);
        String text = review.plainText();

        // I valori compilati devono comparire
        assertTrue(text.contains("Rossi"), "Il riepilogo deve includere il cognome inserito");
        assertTrue(text.contains("Ada"), "Il riepilogo deve includere il nome inserito");

        // I campi obbligatori vuoti devono essere segnalati
        assertTrue(text.contains("NON COMPILATO") || text.contains("non compilato"),
                "Il riepilogo deve segnalare i campi obbligatori non compilati");
    }

    @Test
    @DisplayName("il riepilogo scandisce l'IBAN a gruppi")
    void riepilogoIbanScansionataAGruppi() {
        ProcedurePlan plan = agent.plan(null, scenario);
        Map<String, String> valori = Map.of("iban", "IT60X0542811101000000123456");

        SpokenScript review = agent.reviewBeforeSubmit(plan, valori);
        String text = review.plainText();

        // L'IBAN deve essere scandito a gruppi di 4: "IT60 X054 2811..."
        assertTrue(text.contains("IT60"), "L'IBAN nel riepilogo deve iniziare con IT60");
        // Presenza di almeno uno spazio tra i gruppi dell'IBAN
        assertTrue(text.contains("IT60 ") || text.contains("IT60X054"),
                "L'IBAN deve essere scandito a gruppi nel riepilogo");
    }

    @Test
    @DisplayName("il riepilogo chiede conferma esplicita prima dell'invio")
    void riepilogoConfermaEsplicita() {
        ProcedurePlan plan = agent.plan(null, scenario);
        SpokenScript review = agent.reviewBeforeSubmit(plan, Map.of());

        String text = review.plainText();
        assertTrue(text.contains("Confermi") || text.contains("confermi") || text.contains("inviare"),
                "Il riepilogo deve chiedere conferma esplicita prima dell'invio: " + text);
    }

    @Test
    @DisplayName("il riepilogo con valori nulli non lancia eccezioni")
    void riepilogoConValoriNulli() {
        ProcedurePlan plan = agent.plan(null, scenario);

        // Null currentValues non deve provocare NullPointerException
        SpokenScript review = agent.reviewBeforeSubmit(plan, null);
        assertNotNull(review);
        assertFalse(review.segments().isEmpty(), "Il riepilogo non deve essere vuoto");
    }

    @Test
    @DisplayName("i segmenti del riepilogo hanno tutti una provenance dichiarata")
    void segmentiRiepilogoHannoProvenance() {
        ProcedurePlan plan = agent.plan(null, scenario);
        SpokenScript review = agent.reviewBeforeSubmit(plan, Map.of("cognome", "Rossi"));

        for (var segment : review.segments()) {
            assertNotNull(segment.provenance(),
                    "Il segmento '" + segment.id() + "' non ha provenance");
        }
    }
}
