package it.visiovoice.model;

import java.util.Objects;

/**
 * L'esito di un controllo su un valore, in una frase che si puo' pronunciare.
 *
 * <p>Il messaggio e' il prodotto, non un dettaglio. "regex mismatch" letto da una sintesi
 * vocale non e' un messaggio: e' un vicolo cieco. Qui ci va cosa c'e' di sbagliato e cosa
 * fare, in italiano.
 *
 * @param spokenMessage la frase da pronunciare. Mai vuota, nemmeno in caso di successo:
 *                      il silenzio dopo un controllo e' indistinguibile dal controllo che non
 *                      e' partito, e la barriera piu' crudele dello scenario e' esattamente
 *                      questa
 * @param provenance    da dove viene il messaggio. I messaggi dei validatori sono
 *                      {@link Provenance#HUMAN_REVIEWED}: sono costanti scritte e riviste da
 *                      una persona, non testo generato a runtime. Marcarli come dedotti
 *                      riempirebbe la procedura di avvertenze inutili, e un'avvertenza che
 *                      compare sempre e' un'avvertenza che nessuno ascolta piu'
 * @param normalizedValue il valore ripulito (spazi, maiuscole) che il validatore ha esaminato,
 *                      utile a rileggerlo all'utente cifra per cifra
 */
public record ValidationResult(
        String fieldId,
        boolean valid,
        Severity severity,
        String spokenMessage,
        Provenance provenance,
        String normalizedValue) {

    public ValidationResult {
        Objects.requireNonNull(spokenMessage, "spokenMessage");
        Objects.requireNonNull(provenance, "provenance");
        severity = severity == null ? (valid ? Severity.OK : Severity.ERROR) : severity;
    }

    public static ValidationResult valid(String fieldId, String spokenMessage, String normalizedValue) {
        return new ValidationResult(fieldId, true, Severity.OK, spokenMessage,
                Provenance.HUMAN_REVIEWED, normalizedValue);
    }

    /** Scorciatoia usata dalle convenzioni del progetto: messaggio costante, esito negativo. */
    public static ValidationResult invalid(String spokenMessage) {
        return invalid(null, spokenMessage);
    }

    public static ValidationResult invalid(String fieldId, String spokenMessage) {
        return new ValidationResult(fieldId, false, Severity.ERROR, spokenMessage,
                Provenance.HUMAN_REVIEWED, null);
    }

    /** Sospetto ma non bloccante: si puo' procedere, conviene risentirlo. */
    public static ValidationResult warning(String fieldId, String spokenMessage, String normalizedValue) {
        return new ValidationResult(fieldId, true, Severity.WARNING, spokenMessage,
                Provenance.HUMAN_REVIEWED, normalizedValue);
    }

    /** L'esito come segmento, per farlo passare dal racconto e non da un canale a parte. */
    public ScriptSegment asSegment(String segmentId) {
        SegmentRole role = switch (severity) {
            case OK -> SegmentRole.NAVIGATION;
            case WARNING -> SegmentRole.WARNING;
            case ERROR -> SegmentRole.ERROR;
        };
        return new ScriptSegment(segmentId, role, spokenMessage, provenance, java.util.List.of());
    }
}
