package it.visiovoice.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Il controllo meccanico che decide se un segmento puo' restare {@link Provenance#AI_REPHRASED}.
 *
 * <p>Sta in {@code model} e non in {@code narration} per la stessa ragione per cui
 * {@link Provenance} sta qui: e' la regola normativa del prodotto, e tre agenti che la
 * implementano ognuno a modo proprio producono tre soglie diverse di onesta'. Chi narra la
 * <b>usa</b>; nessuno la riscrive.
 *
 * <p>Nessuna chiamata a un modello, in nessun punto. Un modello che verifica un altro modello
 * ne condivide i punti ciechi, e l'unica parte del sistema di cui possiamo dimostrare la
 * correttezza e' quella che non ne usa nessuno. Qui dentro ci sono due espressioni regolari e
 * una ricerca di sottostringa: gira in microsecondi ed e' riproducibile.
 *
 * <h2>Cosa questo controllo non sa fare</h2>
 * Prende i numeri inventati e i nomi inventati. <b>Non</b> prende una parafrasi col senso
 * rovesciato e i numeri giusti: "entro il 30 giugno" che diventa "dopo il 30 giugno" passa
 * indisturbata. Quel limite e' dichiarato in {@link #SEMANTIC_LIMIT_NOTE} e finisce dentro
 * ogni {@link FidelityReport}, perche' un controllo che tace sui propri limiti fa esattamente
 * il danno che vorrebbe prevenire: far credere verificato cio' che non lo e'.
 */
public final class ProvenanceGate {

    /** Il limite del controllo, in una frase, da riportare in ogni rapporto. */
    public static final String SEMANTIC_LIMIT_NOTE =
            "Questo controllo verifica solo che numeri e nomi propri si ritrovino nella fonte. "
            + "Una riformulazione con i numeri giusti e il significato rovesciato lo supera: "
            + "il controllo del significato resta umano.";

    /** Numeri con separatori: gruppi di cifre uniti da punto o virgola. */
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");

    /** Parole, apostrofi compresi: la base da cui si riconoscono i nomi propri. */
    private static final Pattern WORD = Pattern.compile("[\\p{L}][\\p{L}\\p{M}\\p{Nd}'\u2019]*");

    /** Questi caratteri aprono una frase nuova: la parola che segue non e' un nome proprio. */
    private static final String SENTENCE_ENDINGS = ".!?:;\n";

    private ProvenanceGate() {
    }

    // ---------------------------------------------------------------- normalizzazione

    /**
     * Porta un numero in forma canonica: separatori di migliaia via, virgola come separatore
     * decimale.
     *
     * <p>Il punto seguito da esattamente tre cifre e' un separatore di migliaia e si toglie; il
     * punto che resta e' un separatore decimale e diventa una virgola. Cosi' le tre forme in cui
     * la stessa cifra compare in una pagina italiana, in un file JSON e in una frase parlata si
     * confrontano fra loro.
     */
    public static String normalizeNumber(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        String previous;
        do {
            previous = s;
            s = s.replaceFirst("(\\d)\\.(\\d{3})", "$1$2");
        } while (!s.equals(previous));
        return s.replace('.', ',');
    }

    /**
     * Normalizza un intero testo per il confronto: numeri in forma canonica, minuscolo, spazi
     * compattati.
     *
     * <p>La normalizzazione dei numeri agisce <b>dentro</b> le sequenze di cifre e non su tutto
     * il testo: togliere ogni punto dall'intero corpus unirebbe la fine di una frase con
     * l'inizio della successiva e creerebbe cifre che nessuno ha scritto, cioe' ancoraggi falsi.
     */
    public static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = NUMBER.matcher(text);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            sb.append(text, last, matcher.start()).append(normalizeNumber(matcher.group()));
            last = matcher.end();
        }
        sb.append(text.substring(last));
        return sb.toString().toLowerCase(Locale.ITALIAN).replaceAll("\\s+", " ").trim();
    }

    // ---------------------------------------------------------------- estrazione

    /** Tutti i numeri del testo, in forma normalizzata e senza duplicati. */
    public static List<String> extractNumbers(String text) {
        if (text == null) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String normalized = normalizeNumber(matcher.group());
            if (!normalized.isEmpty() && !found.contains(normalized)) {
                found.add(normalized);
            }
        }
        return List.copyOf(found);
    }

    /**
     * I nomi propri: token che iniziano per maiuscola e non stanno a inizio di frase.
     *
     * <p>L'esclusione dell'inizio di frase non e' una finezza: senza di essa la prima parola di
     * ogni frase finirebbe fra i nomi propri e il gate retrocederebbe qualunque testo, che
     * equivale a non avere un gate.
     */
    public static List<String> extractProperNouns(String text) {
        if (text == null) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < 2 || !Character.isUpperCase(token.charAt(0))) {
                continue;
            }
            if (opensSentence(text, matcher.start())) {
                continue;
            }
            String normalized = token.toLowerCase(Locale.ITALIAN);
            if (!found.contains(normalized)) {
                found.add(normalized);
            }
        }
        return List.copyOf(found);
    }

    private static boolean opensSentence(String text, int start) {
        for (int i = start - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == '"' || c == '(' || c == '\u00ab') {
                continue;
            }
            return SENTENCE_ENDINGS.indexOf(c) >= 0;
        }
        return true;
    }

    // ---------------------------------------------------------------- il gate

    /**
     * Controlla un segmento contro un corpus, senza modificarlo.
     *
     * <p>Solo i segmenti {@link Provenance#AI_REPHRASED} vengono esaminati.
     * {@link Provenance#SOURCE_VERBATIM} e' testo copiato: sottoporlo al controllo lo
     * retrocederebbe ogni volta che la fonte usa una forma diversa da se stessa.
     * {@link Provenance#AI_INFERRED} e' gia' il livello piu' debole, e
     * {@link Provenance#HUMAN_REVIEWED} e' una firma umana, che un'espressione regolare non ha
     * titolo a revocare.
     */
    public static AnchorCheck check(ScriptSegment segment, String corpus) {
        Objects.requireNonNull(segment, "segment");
        if (segment.provenance() != Provenance.AI_REPHRASED) {
            return AnchorCheck.ok(segment.provenance());
        }
        String haystack = normalizeText(corpus);
        List<UnanchoredToken> orphans = new ArrayList<>();
        for (String number : extractNumbers(segment.text())) {
            if (!haystack.contains(number)) {
                orphans.add(new UnanchoredToken(segment.id(), TokenKind.NUMBER, number, number));
            }
        }
        for (String noun : extractProperNouns(segment.text())) {
            if (!haystack.contains(noun)) {
                orphans.add(new UnanchoredToken(segment.id(), TokenKind.PROPER_NOUN, noun, noun));
            }
        }
        return orphans.isEmpty()
                ? AnchorCheck.ok(Provenance.AI_REPHRASED)
                : new AnchorCheck(false, Provenance.AI_INFERRED, orphans);
    }

    /** Il segmento come esce dal gate: retrocesso se non si ancora, identico se si ancora. */
    public static ScriptSegment enforce(ScriptSegment segment, String corpus) {
        AnchorCheck result = check(segment, corpus);
        return result.anchored() ? segment : segment.withProvenance(result.effective());
    }

    /**
     * Il corpus contro cui va controllato un segmento: i fatti che il segmento stesso cita,
     * piu' un corpus comune.
     *
     * <p>Per segmento e non globale: un segmento che cita un fatto non deve poter essere
     * salvato da un numero che sta in un fatto di cui non ha mai parlato. Un riferimento che
     * non corrisponde a nessun fatto viene aggiunto cosi' com'e', perche' puo' essere un
     * selettore del DOM o una citazione diretta.
     *
     * <p>I {@link ComputedFact} non entrano mai qui: un valore calcolato che ancorasse se
     * stesso farebbe dire al gate "verificato sulla fonte" di un numero che nessuna fonte
     * contiene.
     */
    public static String corpusFor(ScriptSegment segment, List<SourceFact> facts, String extraCorpus) {
        Objects.requireNonNull(segment, "segment");
        List<SourceFact> available = facts == null ? List.of() : facts;
        StringBuilder sb = new StringBuilder();
        for (String ref : segment.sourceRefs()) {
            available.stream()
                    .filter(f -> f.id().equals(ref))
                    .findFirst()
                    .ifPresentOrElse(
                            f -> sb.append(f.text()).append('\n').append(f.ref()).append('\n'),
                            () -> sb.append(ref).append('\n'));
        }
        if (extraCorpus != null) {
            sb.append(extraCorpus);
        }
        return sb.toString();
    }

    /**
     * Passa tutto lo script al gate. E' la firma che usa {@code narration}.
     *
     * @param extraCorpus testo sempre ammesso come fonte, tipicamente
     *                    {@link ScreenModel#textCorpus()}: cio' che sta scritto nella pagina e'
     *                    una fonte legittima anche quando il segmento non la cita
     */
    public static FidelityVerdict verify(SpokenScript script, List<SourceFact> facts, String extraCorpus) {
        Objects.requireNonNull(script, "script");
        List<ScriptSegment> checked = new ArrayList<>();
        List<String> demoted = new ArrayList<>();
        List<UnanchoredToken> orphans = new ArrayList<>();
        for (ScriptSegment segment : script.segments()) {
            AnchorCheck result = check(segment, corpusFor(segment, facts, extraCorpus));
            if (result.anchored()) {
                checked.add(segment);
            } else {
                checked.add(segment.withProvenance(result.effective()));
                demoted.add(segment.id());
                orphans.addAll(result.unanchored());
            }
        }
        List<String> reviewNeeded = new ArrayList<>();
        reviewNeeded.add(SEMANTIC_LIMIT_NOTE);
        demoted.forEach(id -> reviewNeeded.add(
                "Segmento " + id + ": contiene valori che non si ritrovano nella fonte citata. "
                + "Va riletto da una persona prima di considerarlo attendibile."));
        FidelityReport report = new FidelityReport(
                script.id(), script.segments().size(), demoted, orphans, reviewNeeded);
        return new FidelityVerdict(script.withSegments(checked), report);
    }

    /** Come sopra, senza corpus aggiuntivo: la forma dichiarata in {@code docs/ARCHITECTURE.md}. */
    public static FidelityVerdict verify(SpokenScript script, List<SourceFact> facts) {
        return verify(script, facts, null);
    }
}
