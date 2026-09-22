package it.visiovoice.model;

import java.util.List;
import java.util.Objects;

/**
 * Una tabella di dati ricostruita da un'immagine, nella forma in cui puo' diventare un
 * {@code <table>} vero con {@code <th scope>}.
 *
 * <p>Esiste come tipo e non come prosa perche' una tabella raccontata a parole si puo'
 * ascoltare ma non si puo' <b>consultare</b>: chi usa uno screen reader su una tabella vera
 * naviga per righe e colonne e chiede "quanto vale questa cella", che e' precisamente cio' che
 * serve per trovare la propria fascia. Riassumerla a parole risolverebbe il requisito formale
 * dell'alternativa testuale lasciando in piedi il problema.
 *
 * @param provenance da dove viene il contenuto delle celle. Una tabella letta da un modello a
 *                   partire da un PNG non e' mai {@link Provenance#SOURCE_VERBATIM}: al
 *                   massimo e' riformulata e riancorata ai fatti dello scenario
 */
public record AccessibleTable(
        String caption,
        List<String> headers,
        List<List<String>> rows,
        Provenance provenance,
        List<String> sourceRefs) {

    public AccessibleTable {
        Objects.requireNonNull(provenance, "provenance");
        headers = List.copyOf(headers == null ? List.of() : headers);
        rows = (rows == null ? List.<List<String>>of() : rows).stream()
                .map(row -> List.copyOf(row == null ? List.<String>of() : row))
                .toList();
        sourceRefs = List.copyOf(sourceRefs == null ? List.of() : sourceRefs);
    }

    /** Tutte le celle di seguito: e' il testo che il gate deve riuscire a riancorare. */
    public String cellText() {
        StringBuilder sb = new StringBuilder();
        if (caption != null) {
            sb.append(caption).append(' ');
        }
        headers.forEach(h -> sb.append(h).append(' '));
        rows.forEach(row -> row.forEach(cell -> sb.append(cell).append(' ')));
        return sb.toString().trim();
    }
}
