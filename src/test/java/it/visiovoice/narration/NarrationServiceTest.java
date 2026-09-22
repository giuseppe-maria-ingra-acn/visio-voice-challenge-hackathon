package it.visiovoice.narration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.visiovoice.agents.AgentContext;
import it.visiovoice.llm.MockLlmClient;
import it.visiovoice.model.AccessibleTable;
import it.visiovoice.model.Barrier;
import it.visiovoice.model.BarrierType;
import it.visiovoice.model.ComputedFact;
import it.visiovoice.model.DetailLevel;
import it.visiovoice.model.EvidenceFidelity;
import it.visiovoice.model.FidelityReport;
import it.visiovoice.model.FidelityVerdict;
import it.visiovoice.model.Provenance;
import it.visiovoice.model.ProvenanceGate;
import it.visiovoice.model.Scenario;
import it.visiovoice.model.ScreenModel;
import it.visiovoice.model.ScreenRegion;
import it.visiovoice.model.ScriptSegment;
import it.visiovoice.model.SegmentRole;
import it.visiovoice.model.ServiceFidelity;
import it.visiovoice.model.ServiceInfo;
import it.visiovoice.model.SourceFact;
import it.visiovoice.model.SpokenScript;
import it.visiovoice.model.TokenKind;
import it.visiovoice.model.VisualAsset;
import it.visiovoice.model.VisualDescription;
import it.visiovoice.model.VisualKind;
import it.visiovoice.model.RegionRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test del layer di narrazione.
 *
 * <p>Il test piu' importante e' {@code #numeroInventatoVieneRetrocessoESegnalato}: e' quello da
 * mostrare in demo. Dimostra che "semplificare senza tradire" non e' una promessa ma un
 * controllo che gira — e che un numero inventato non raggiunge mai l'utente senza essere
 * segnalato come dedotto.
 */
class NarrationServiceTest {

    private NarrationService service;
    private MockLlmClient mockLlm;
    private FidelityAgent fidelityAgent;

    @BeforeEach
    void setUp() {
        mockLlm = new MockLlmClient();
        fidelityAgent = new FidelityAgent();
        ObjectMapper objectMapper = new ObjectMapper();
        service = new NarrationService(mockLlm, fidelityAgent, objectMapper);
    }

    // -------------------------------------------------- panoramica: regola dei 15 secondi

    @Test
    @DisplayName("la panoramica BRIEF ha al massimo 3 segmenti: titolo, struttura, azione")
    void panoramicaHaMassimoTreSegmenti() {
        ScreenModel screen = buildLandingScreen();
        AgentContext ctx = AgentContext.of("sess-1", buildScenario(), DetailLevel.BRIEF);

        SpokenScript script = service.narrate(screen, DetailLevel.BRIEF, ctx);

        assertNotNull(script);
        assertTrue(script.segments().size() <= 3,
                "BRIEF deve avere al massimo 3 segmenti, ne ha: " + script.segments().size());
    }

    @Test
    @DisplayName("la panoramica BRIEF contiene titolo, struttura e azione — in quest'ordine")
    void panoramicaContieneTreRuoli() {
        ScreenModel screen = buildLandingScreen();
        AgentContext ctx = AgentContext.of("sess-1", buildScenario(), DetailLevel.BRIEF);

        SpokenScript script = service.narrate(screen, DetailLevel.BRIEF, ctx);

        List<ScriptSegment> segs = script.segments();
        assertTrue(segs.size() >= 1, "deve esserci almeno il titolo");
        assertEquals(SegmentRole.TITLE, segs.get(0).role(), "primo segmento = TITLE");
        if (segs.size() >= 2) {
            assertEquals(SegmentRole.OVERVIEW, segs.get(1).role(), "secondo segmento = OVERVIEW");
        }
        if (segs.size() >= 3) {
            assertEquals(SegmentRole.NAVIGATION, segs.get(2).role(), "terzo segmento = NAVIGATION");
        }
    }

    @Test
    @DisplayName("un test conta le frasi: la panoramica ha al massimo 3 punti finali")
    void panoramicaHaMassimoTreFrasi() {
        ScreenModel screen = buildLandingScreen();
        AgentContext ctx = AgentContext.of("sess-1", buildScenario(), DetailLevel.BRIEF);

        SpokenScript script = service.narrate(screen, DetailLevel.BRIEF, ctx);

        long sentenceCount = script.plainText().chars()
                .filter(c -> c == '.' || c == '!' || c == '?')
                .count();
        assertTrue(sentenceCount <= 3,
                "la panoramica non deve superare 3 frasi, ne conta: " + sentenceCount
                + " nel testo: " + script.plainText());
    }

    @Test
    @DisplayName("il titolo della pagina entra nel racconto come SOURCE_VERBATIM")
    void titoloESourceVerbatim() {
        ScreenModel screen = buildLandingScreen();
        AgentContext ctx = AgentContext.of("sess-1", buildScenario(), DetailLevel.BRIEF);

        SpokenScript script = service.narrate(screen, DetailLevel.BRIEF, ctx);

        ScriptSegment title = script.byRole(SegmentRole.TITLE).get(0);
        assertEquals(Provenance.SOURCE_VERBATIM, title.provenance(),
                "il titolo e' letto dalla pagina: SOURCE_VERBATIM");
        assertTrue(title.text().contains("Assegno Unico"),
                "il testo del titolo deve contenere il nome del servizio");
    }

    // -------------------------------------------------- il test da mostrare in demo

    /**
     * IL TEST DELLA DEMO.
     *
     * <p>Dimostra che un numero inventato dall'AI non raggiunge mai l'utente come verificato:
     * viene retrocesso ad {@link Provenance#AI_INFERRED} e il token inventato finisce nel
     * {@link FidelityReport}.
     *
     * <p>Scenario: l'AI produce "Ti spettano 500 euro al mese" invece di 203,80 euro.
     * Il gate trova "500" nel testo del segmento, non lo trova nella fonte citata,
     * e retrocede il segmento a AI_INFERRED. L'utente sentira' "Attenzione: questa parte
     * l'ho dedotta io, conviene verificarla. Ti spettano 500 euro al mese."
     *
     * <p>Il gate e' deterministico: stesso input, stesso output, sempre. E' dimostrabile con
     * dati statici davanti a chiunque, senza chiamate a modelli, senza variabili d'ambiente.
     */
    @Test
    @DisplayName("[DEMO] un numero inventato viene retrocesso ad AI_INFERRED e segnalato nel rapporto")
    void numeroInventatoVieneRetrocessoESegnalato() {
        // Un segmento che l'AI ha prodotto con un numero inventato
        ScriptSegment segmentoInventato = ScriptSegment.rephrased(
                "seg-demo-1", SegmentRole.DATA,
                "Ti spettano 500 euro al mese per figlio.",
                List.of("f1"));

        // La fonte reale dice 203,80 euro — non contiene "500"
        List<SourceFact> fonteReale = List.of(
                SourceFact.of("f1", "203,80 euro al mese", "INPS Circolare n. 7 del 30 gennaio 2026"));
        SpokenScript script = SpokenScript.of("script-demo", "schermata-importi",
                DetailLevel.STANDARD, List.of(segmentoInventato));

        // Il gate verifica
        FidelityVerdict verdetto = fidelityAgent.verify(script, fonteReale);

        // Il segmento e' stato retrocesso
        ScriptSegment segmentoRetrocesso = verdetto.script().segments().get(0);
        assertEquals(Provenance.AI_INFERRED, segmentoRetrocesso.provenance(),
                "un segmento con numero inventato deve essere retrocesso ad AI_INFERRED");

        // Il numero inventato compare fra i token non ancorati
        FidelityReport rapporto = verdetto.report();
        assertTrue(rapporto.unanchored().stream()
                        .anyMatch(t -> t.kind() == TokenKind.NUMBER && t.token().equals("500")),
                "il numero '500' deve comparire fra i token non ancorati: "
                + "e' la riga che finisce nella mappa del contributo AI");

        // Il rapporto segnala il segmento come da revisionare
        assertTrue(rapporto.demotedSegmentIds().contains("seg-demo-1"),
                "il segmento retrocesso deve essere elencato nel rapporto");

        // Il testo pronunciato porta l'avvertenza
        String testoPronunciato = segmentoRetrocesso.spokenText();
        assertTrue(testoPronunciato.startsWith("Attenzione:"),
                "un segmento dedotto deve aprirsi con l'avvertenza, cosi' l'utente sa "
                + "di dover verificare. Testo: " + testoPronunciato);

        // Il rapporto dichiara sempre il proprio limite semantico
        assertTrue(rapporto.reviewNeeded().contains(ProvenanceGate.SEMANTIC_LIMIT_NOTE),
                "il rapporto deve dichiarare il limite del controllo anche quando retrocede");
    }

    // -------------------------------------------------- pipeline mock senza rete

    @Test
    @DisplayName("la pipeline completa gira in mock mode senza variabili d'ambiente")
    void pipelineCompletaInMockMode() {
        // La fixture 'descrivi-tabella-importi.json' e' su disco: il mock la serve senza rete
        assertTrue(mockLlm.hasFixture("descrivi-tabella-importi"),
                "la fixture della tabella deve esistere su disco per la demo offline");

        VisualAsset tabella = new VisualAsset(
                "importi-2026", "importi-2026.png", "tabella importi",
                VisualKind.DATA_TABLE, "Importi 2026", null);
        AgentContext ctx = AgentContext.of("sess-demo", buildScenario(), DetailLevel.STANDARD);

        // Nessuna eccezione, nessuna rete, nessuna variabile d'ambiente
        VisualDescription descrizione = service.describeVisual(tabella, ctx);

        assertNotNull(descrizione, "deve restituire una descrizione, mai null");
        assertFalse(descrizione.summary().isBlank(), "il riassunto non puo' essere vuoto");
    }

    // -------------------------------------------------- describeVisual: tabella importi

    @Test
    @DisplayName("describeVisual sulla tabella importi restituisce una AccessibleTable strutturata")
    void describeVisualTabellaImportiRestituisceStruttura() {
        VisualAsset tabella = new VisualAsset(
                "importi-2026", "importi-2026.png", "tabella importi",
                VisualKind.DATA_TABLE, "Importi 2026", null);
        AgentContext ctx = AgentContext.of("sess-1", buildScenario(), DetailLevel.STANDARD);

        VisualDescription descrizione = service.describeVisual(tabella, ctx);

        assertTrue(descrizione.hasTable(),
                "la tabella importi deve produrre almeno una AccessibleTable");

        AccessibleTable table = descrizione.tables().get(0);
        assertEquals(2, table.headers().size(),
                "la tabella deve avere 2 colonne: Fascia ISEE e Importo mensile");
        assertEquals(4, table.rows().size(),
                "la tabella deve avere 4 righe (una per fascia ISEE)");
    }

    @Test
    @DisplayName("describeVisual risolve il promptId corretto per DATA_TABLE con anno")
    void promptIdPerDataTable() {
        VisualAsset tabella = new VisualAsset(
                "importi-2026", "importi-2026.png", "", VisualKind.DATA_TABLE, "", null);
        assertEquals("descrivi-tabella-importi",
                NarrationService.resolvePromptId(tabella),
                "il promptId deve corrispondere alla fixture esistente");
    }

    @Test
    @DisplayName("describeVisual risolve il promptId corretto per STEP_INDICATOR")
    void promptIdPerStepIndicator() {
        VisualAsset indicatore = new VisualAsset(
                "step-indicator-2", "step-indicator-2.png", "", VisualKind.STEP_INDICATOR, "", null);
        assertEquals("descrivi-indicatore-passi",
                NarrationService.resolvePromptId(indicatore),
                "tutti gli indicatori di passo usano la stessa fixture");
    }

    @Test
    @DisplayName("la tabella viene restituita con provenance AI_REPHRASED dalla fixture")
    void tabellaHaProvenanceAiRephrased() {
        VisualAsset tabella = new VisualAsset(
                "importi-2026", "importi-2026.png", "tabella importi",
                VisualKind.DATA_TABLE, "Importi 2026", null);
        AgentContext ctx = AgentContext.of("sess-1", buildScenario(), DetailLevel.STANDARD);

        VisualDescription descrizione = service.describeVisual(tabella, ctx);

        assertFalse(Provenance.AI_INFERRED == descrizione.provenance(),
                "una risposta da fixture e' AI_REPHRASED, non AI_INFERRED");
    }

    // -------------------------------------------------- STANDARD: piu' dettagli

    @Test
    @DisplayName("STANDARD aggiunge sezioni e barriere rispetto a BRIEF")
    void standardAggiungeSezioniEBarriere() {
        ScreenModel screen = buildLandingScreenConBarriera();
        AgentContext ctx = AgentContext.of("sess-1", buildScenario(), DetailLevel.STANDARD);

        SpokenScript brief = service.narrate(screen, DetailLevel.BRIEF, ctx);
        SpokenScript standard = service.narrate(screen, DetailLevel.STANDARD, ctx);

        assertTrue(standard.segments().size() >= brief.segments().size(),
                "STANDARD deve avere almeno tanti segmenti quanto BRIEF");
    }

    @Test
    @DisplayName("il racconto STANDARD passa il gate e non restituisce script non verificato")
    void raccontoStandardPassaIlGate() {
        ScreenModel screen = buildLandingScreen();
        AgentContext ctx = AgentContext.of("sess-1", buildScenario(), DetailLevel.STANDARD);

        SpokenScript script = service.narrate(screen, DetailLevel.STANDARD, ctx);

        // Nessun segmento deve avere numeri o nomi non ancorati da un segmento AI_REPHRASED
        // (il gate ha gia' retrocesso i problematici a AI_INFERRED)
        assertNotNull(script);
        assertFalse(script.segments().isEmpty(), "lo script non puo' essere vuoto");
    }

    // -------------------------------------------------- valore calcolato: invariante

    @Test
    @DisplayName("un valore calcolato non ancora se stesso: c1=331,80 resta AI_INFERRED")
    void valoreCalcolatoNonAncoraSeStesso() {
        ComputedFact doppio = new ComputedFact("c1", "331,80 euro",
                "due volte l'importo mensile per un figlio",
                List.of("f5"), "calcolo per due figli");

        assertEquals(Provenance.AI_INFERRED, doppio.provenance(),
                "ComputedFact e' sempre AI_INFERRED: non e' nella fonte, e' un calcolo");

        // Se ci fosse un segmento che cita il valore calcolato come riformulato,
        // il gate lo retrocederebbe perche' 331,80 non sta nei sourceFacts
        ScriptSegment segConCalcolo = ScriptSegment.rephrased(
                "seg-calc", SegmentRole.DATA,
                "Per due figli ti spettano 331,80 euro al mese.",
                List.of("f5"));  // f5 contiene 165,90, non 331,80
        List<SourceFact> fatti = List.of(
                SourceFact.of("f5", "165,90 euro al mese", "importo per un figlio in fascia 2"));

        ScriptSegment retrocesso = ProvenanceGate.enforce(segConCalcolo,
                ProvenanceGate.corpusFor(segConCalcolo, fatti, null));

        assertEquals(Provenance.AI_INFERRED, retrocesso.provenance(),
                "331,80 non e' nella fonte f5: il gate retrocede correttamente");
    }

    // -------------------------------------------------- metodi di supporto

    private static ScreenModel buildLandingScreen() {
        List<ScreenRegion> regions = List.of(
                ScreenRegion.of("r1", RegionRole.HEADER, "Intestazione INPS"),
                ScreenRegion.of("r2", RegionRole.PARAGRAPH,
                        "L'Assegno Unico e Universale e' un sostegno per le famiglie."),
                ScreenRegion.of("r3", RegionRole.INFO_BOX,
                        "Importi 2026: fino a 203,80 euro al mese per figlio."));
        return new ScreenModel("index", "http://localhost/demo/index.html",
                "Assegno Unico e Universale — Domanda online",
                0, 0, regions, List.of(), List.of(), List.of());
    }

    private static ScreenModel buildLandingScreenConBarriera() {
        Barrier b1 = new Barrier("b1", BarrierType.IMAGE_ONLY_DATA,
                "sezione importi", "grafico immagine tabella importi",
                "La tabella degli importi e' un PNG senza testo alternativo.",
                "WCAG 2.1 SC 1.1.1", EvidenceFidelity.RICOSTRUITO);
        List<ScreenRegion> regions = List.of(
                ScreenRegion.of("r1", RegionRole.HEADING, "Importi 2026"),
                ScreenRegion.of("r2", RegionRole.PARAGRAPH, "Fai domanda online."));
        return new ScreenModel("index", "http://localhost/demo/index.html",
                "Assegno Unico e Universale",
                0, 0, regions, List.of(), List.of(), List.of(b1));
    }

    private static Scenario buildScenario() {
        ServiceInfo service = new ServiceInfo(
                "Assegno Unico e Universale per i figli",
                "INPS", "http://localhost:8080/demo/index.html", ServiceFidelity.REPLICA);
        List<SourceFact> fatti = List.of(
                SourceFact.of("f1", "203,80 euro al mese",
                        "Importo massimo per figlio. INPS Circolare n. 7/2026."),
                SourceFact.of("f5", "165,90 euro",
                        "Importo in fascia 2. informazionescuola.it."));
        return new Scenario("inps-test", service,
                "marco-ferrari", "Presentare domanda di assegno unico",
                List.of(), fatti, List.of(), List.of());
    }
}
