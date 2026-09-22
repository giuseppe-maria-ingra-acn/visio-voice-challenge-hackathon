package it.visiovoice.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * I casi obbligatori della tabella in {@code docs/PROVENANCE-SPEC.md}.
 *
 * <p>E' il gate meccanico del requisito "semplificare senza tradire". Due di questi test -
 * quello sul numero inventato e quello sul nome proprio inventato - diventano verdi solo se la
 * retrocessione esiste davvero: se qualcuno svuotasse {@link ProvenanceGate#enforce} facendola
 * diventare l'identita', fallirebbero subito. Un test che passa in ogni caso non e' un gate,
 * e' un ornamento.
 */
class ProvenanceInvariantTest {

    private static final String FONTE_IMPORTO =
            "Importo massimo per figlio minorenne: 149,00 euro al mese.";

    private static ScriptSegment rephrased(String text) {
        return ScriptSegment.rephrased("seg-1", SegmentRole.DATA, text, List.of("f1"));
    }

    // -------------------------------------------------- caso 1: numero presente

    @Test
    @DisplayName("caso 1: un numero che sta nella fonte non fa retrocedere il segmento")
    void numeroPresenteNellaFonteRestaRiformulato() {
        ScriptSegment segment = rephrased("Ti spettano 149 euro al mese.");

        ScriptSegment out = ProvenanceGate.enforce(segment, FONTE_IMPORTO);

        assertEquals(Provenance.AI_REPHRASED, out.provenance());
    }

    // -------------------------------------------------- caso 2: numero inventato

    @Test
    @DisplayName("caso 2: un numero che non sta nella fonte retrocede il segmento a dedotto")
    void numeroInventatoRetrocedeAdAiInferred() {
        ScriptSegment segment = rephrased("Ti spettano 199 euro al mese.");

        ScriptSegment out = ProvenanceGate.enforce(segment, FONTE_IMPORTO);

        assertEquals(Provenance.AI_INFERRED, out.provenance());
    }

    @Test
    @DisplayName("caso 2: il numero inventato finisce fra i token non ancorati del rapporto")
    void numeroInventatoCompareInUnanchored() {
        SpokenScript script = SpokenScript.of("sc-1", "schermo-1", DetailLevel.STANDARD,
                List.of(rephrased("Ti spettano 199 euro al mese.")));
        List<SourceFact> facts = List.of(SourceFact.of("f1", "149,00 euro al mese", "circolare"));

        FidelityVerdict verdict = ProvenanceGate.verify(script, facts);

        assertTrue(verdict.report().unanchored().stream()
                        .anyMatch(t -> t.kind() == TokenKind.NUMBER && t.token().equals("199")),
                "il numero inventato deve comparire fra i token non ancorati: e' la riga che "
                + "finisce nella mappa del contributo AI");
    }

    // -------------------------------------------------- caso 3: separatore di migliaia

    @Test
    @DisplayName("caso 3: il separatore delle migliaia non fa fallire l'ancoraggio")
    void separatoreDelleMigliaiaNonFaFallireLAncoraggio() {
        ScriptSegment segment = rephrased("La soglia e' 17200.");

        ScriptSegment out = ProvenanceGate.enforce(segment, "Soglia ISEE: 17.200");

        assertEquals(Provenance.AI_REPHRASED, out.provenance());
    }

    // -------------------------------------------------- caso 4: virgola decimale

    @Test
    @DisplayName("caso 4: migliaia e decimali insieme si riconducono alla stessa forma")
    void virgolaDecimaleEPuntoDelleMigliaiaSiRiconducono() {
        ScriptSegment segment = rephrased("Il valore dichiarato e' 1234,50.");

        ScriptSegment out = ProvenanceGate.enforce(segment, "Valore: 1.234,50");

        assertEquals(Provenance.AI_REPHRASED, out.provenance());
    }

    // -------------------------------------------------- caso 5: nome proprio inventato

    @Test
    @DisplayName("caso 5: un nome proprio che non sta nella fonte retrocede il segmento")
    void nomeProprioInventatoRetrocedeAdAiInferred() {
        ScriptSegment segment = rephrased("La domanda si presenta allo sportello di Bologna.");

        ScriptSegment out = ProvenanceGate.enforce(segment,
                "La domanda si presenta online sul portale.");

        assertEquals(Provenance.AI_INFERRED, out.provenance());
    }

    @Test
    @DisplayName("caso 5: un nome proprio che sta nella fonte non fa retrocedere nulla")
    void nomeProprioPresenteNellaFonteNonRetrocede() {
        ScriptSegment segment = rephrased("La domanda si presenta sul portale INPS.");

        ScriptSegment out = ProvenanceGate.enforce(segment, "Portale INPS, domanda online.");

        assertEquals(Provenance.AI_REPHRASED, out.provenance());
    }

    // -------------------------------------------------- caso 6: composizione

    @Test
    @DisplayName("caso 6: componendo due provenance vince la piu' debole")
    void componendoVinceLAnelloDebole() {
        assertEquals(Provenance.AI_INFERRED,
                Provenance.weakest(Provenance.SOURCE_VERBATIM, Provenance.AI_INFERRED));
    }

    @Test
    @DisplayName("caso 6: un racconto vale quanto il suo segmento piu' debole")
    void loScriptValeQuantoIlSuoSegmentoPiuDebole() {
        SpokenScript script = SpokenScript.of("sc-2", "schermo-1", DetailLevel.STANDARD, List.of(
                ScriptSegment.verbatim("s1", SegmentRole.TITLE, "Domanda di assegno", List.of()),
                ScriptSegment.inferred("s2", SegmentRole.DATA, "In tutto sono due figli.", List.of())));

        assertEquals(Provenance.AI_INFERRED, script.weakestProvenance());
    }

    // -------------------------------------------------- caso 7: verbatim intoccabile

    @Test
    @DisplayName("caso 7: un segmento copiato dalla pagina non retrocede, numeri compresi")
    void verbatimNonVieneMaiRetrocesso() {
        ScriptSegment segment = ScriptSegment.verbatim("seg-9", SegmentRole.DATA,
                "203,80 euro al mese per ogni figlio.", List.of("f1"));

        ScriptSegment out = ProvenanceGate.enforce(segment, "una fonte che non contiene quei numeri");

        assertEquals(Provenance.SOURCE_VERBATIM, out.provenance());
    }

    @Test
    @DisplayName("caso 7: una revisione umana non viene revocata da un'espressione regolare")
    void humanReviewedNonVieneRetrocesso() {
        ScriptSegment segment = new ScriptSegment("seg-10", SegmentRole.HELP,
                "Questo IBAN non supera il controllo: rileggilo e riprova.",
                Provenance.HUMAN_REVIEWED, List.of());

        assertEquals(Provenance.HUMAN_REVIEWED, ProvenanceGate.enforce(segment, "").provenance());
    }

    // -------------------------------------------------- caso 8: nessun numero

    @Test
    @DisplayName("caso 8: un testo senza cifre ne' nomi propri resta riformulato")
    void testoSenzaNumeriNeNomiPropriResta() {
        ScriptSegment segment = rephrased("Adesso devi inserire il tuo codice fiscale.");

        ScriptSegment out = ProvenanceGate.enforce(segment, "campo del codice fiscale");

        assertEquals(Provenance.AI_REPHRASED, out.provenance());
    }

    // -------------------------------------------------- effetti sull'interfaccia

    @Test
    @DisplayName("un segmento dedotto porta con se' l'avvertenza da pronunciare")
    void ilSegmentoDedottoSiAnnunciaComeDedotto() {
        ScriptSegment segment = ScriptSegment.inferred("seg-11", SegmentRole.DATA,
                "In tutto fa il doppio dell'importo per un figlio.", List.of("c1"));

        assertTrue(segment.spokenText().startsWith("Attenzione:"),
                "l'avvertenza non deve dipendere da chi costruisce l'interfaccia");
    }

    @Test
    @DisplayName("il rapporto dichiara il limite del controllo anche quando e' pulito")
    void ilRapportoDichiaraIlProprioLimite() {
        SpokenScript script = SpokenScript.of("sc-3", "schermo-1", DetailLevel.STANDARD,
                List.of(rephrased("Adesso serve il codice fiscale.")));

        FidelityVerdict verdict = ProvenanceGate.verify(script, List.of());

        assertTrue(verdict.report().isClean());
        assertTrue(verdict.report().reviewNeeded().contains(ProvenanceGate.SEMANTIC_LIMIT_NOTE),
                "un controllo che tace sui propri limiti fa credere verificato cio' che non lo e'");
    }

    @Test
    @DisplayName("un valore calcolato resta dedotto: non entra nel corpus e non ancora se stesso")
    void ilValoreCalcolatoNonAncoraSeStesso() {
        ComputedFact doppio = new ComputedFact("c1", "331,80 euro",
                "due volte l'importo mensile per un figlio", List.of("f5"), "calcolo per due figli");

        assertEquals(Provenance.AI_INFERRED, doppio.provenance());
        assertTrue(doppio.provenance().needsHumanReview());
        assertFalse(doppio.derivedFrom().isEmpty(),
                "un calcolo senza i fatti da cui deriva non e' controllabile a mano");
    }
}
