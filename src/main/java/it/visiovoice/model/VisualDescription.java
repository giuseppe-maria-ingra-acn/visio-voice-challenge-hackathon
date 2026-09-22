package it.visiovoice.model;

import java.util.List;
import java.util.Objects;

/**
 * Cosa c'e' dentro un'immagine, detto a chi non la vede.
 *
 * <p>Due uscite e non una: {@code summary} per orientarsi, {@code tables} per consultare. Se
 * l'immagine porta dati tabellari, il riassunto non basta e la tabella non e' un extra.
 *
 * @param provenance da dove viene questa descrizione. Se l'ha prodotta un modello a partire da
 *                   un PNG e i numeri non si ritrovano nei fatti dello scenario, resta
 *                   {@link Provenance#AI_INFERRED}, e l'utente lo sente
 */
public record VisualDescription(
        String assetId,
        String summary,
        List<AccessibleTable> tables,
        Provenance provenance,
        List<String> sourceRefs) {

    public VisualDescription {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(provenance, "provenance");
        tables = List.copyOf(tables == null ? List.of() : tables);
        sourceRefs = List.copyOf(sourceRefs == null ? List.of() : sourceRefs);
    }

    public boolean hasTable() {
        return !tables.isEmpty();
    }
}
