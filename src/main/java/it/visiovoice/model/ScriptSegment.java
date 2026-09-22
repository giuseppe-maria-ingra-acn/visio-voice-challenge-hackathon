package it.visiovoice.model;

import java.util.List;
import java.util.Objects;

/**
 * Una frase che l'utente ascoltera', insieme a da dove viene.
 *
 * <p>E' il tipo centrale del progetto. Tutto il testo che raggiunge l'utente passa da qui, e
 * qui la provenance e' un parametro del costruttore con {@code requireNonNull}: non esiste un
 * modo di costruire un segmento e dimenticarsi di dichiararne l'origine. Un valore di default
 * silenzioso sarebbe una falla nell'unica funzione di sicurezza che il prodotto ha.
 *
 * @param id        identificativo stabile: e' la chiave con cui l'utente puo' richiedere la
 *                  provenienza di questa frase dopo averla sentita
 * @param role      a cosa serve questa frase nel racconto
 * @param text      il testo in italiano, pronunciabile: nessun gergo, nessun codice di errore
 * @param provenance da dove viene. Mai {@code null}
 * @param sourceRefs gli id dei {@link SourceFact} o i selettori DOM su cui questo testo si
 *                  appoggia. Sono il corpus su cui {@link ProvenanceGate} riancora i numeri:
 *                  un segmento marcato {@link Provenance#AI_REPHRASED} senza riferimenti non
 *                  ha nulla a cui essere ancorato, e il gate lo retrocede
 */
public record ScriptSegment(
        String id,
        SegmentRole role,
        String text,
        Provenance provenance,
        List<String> sourceRefs) {

    public ScriptSegment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(provenance, "provenance");
        sourceRefs = List.copyOf(sourceRefs == null ? List.of() : sourceRefs);
    }

    /** Testo copiato letterale dalla pagina. */
    public static ScriptSegment verbatim(String id, SegmentRole role, String text, List<String> sourceRefs) {
        return new ScriptSegment(id, role, text, Provenance.SOURCE_VERBATIM, sourceRefs);
    }

    /**
     * Testo riformulato dall'AI. Attenzione: costruirlo cosi' e' una <em>candidatura</em>, non
     * una promozione. Il livello resta tale solo se {@link ProvenanceGate} ritrova ogni numero
     * e ogni nome proprio nel corpus dei {@code sourceRefs}.
     */
    public static ScriptSegment rephrased(String id, SegmentRole role, String text, List<String> sourceRefs) {
        return new ScriptSegment(id, role, text, Provenance.AI_REPHRASED, sourceRefs);
    }

    /** Testo dedotto: un calcolo, un nesso, una spiegazione. L'utente lo sentira' dichiarato. */
    public static ScriptSegment inferred(String id, SegmentRole role, String text, List<String> sourceRefs) {
        return new ScriptSegment(id, role, text, Provenance.AI_INFERRED, sourceRefs);
    }

    /** Copia con un'altra provenance. Unico modo di cambiarla: non c'e' alcun setter. */
    public ScriptSegment withProvenance(Provenance newProvenance) {
        return new ScriptSegment(id, role, text, newProvenance, sourceRefs);
    }

    /**
     * Retrocessione a {@link Provenance#AI_INFERRED}, cioe' cio' che il gate fa quando un
     * numero non si ritrova nella fonte. Metodo con un nome proprio invece di
     * {@code withProvenance(AI_INFERRED)} sparso in tre package: cosi' si trova con un grep.
     */
    public ScriptSegment demoted() {
        return provenance == Provenance.AI_INFERRED ? this : withProvenance(Provenance.AI_INFERRED);
    }

    /** Se true l'interfaccia deve pronunciare l'avvertenza prima del testo. */
    public boolean needsHumanReview() {
        return provenance.needsHumanReview();
    }

    /**
     * Il testo come va pronunciato, avvertenza compresa. Esiste per non lasciare a tre
     * frontend diversi la scelta di quando anteporre l'avvertenza.
     */
    public String spokenText() {
        String disclaimer = provenance.spokenDisclaimer();
        return disclaimer.isEmpty() ? text : disclaimer + " " + text;
    }
}
