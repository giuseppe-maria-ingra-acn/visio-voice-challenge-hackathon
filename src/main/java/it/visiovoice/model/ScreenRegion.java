package it.visiovoice.model;

import java.util.Objects;

/**
 * Un pezzo di pagina che ha un senso per conto proprio.
 *
 * <p>Non e' un nodo del DOM: e' cio' che resta del DOM quando si toglie il markup di
 * presentazione. Un {@code div} annidato sei volte per fare un riquadro colorato e' una
 * regione sola, e va raccontato una volta sola.
 */
public record ScreenRegion(
        String id,
        RegionRole role,
        String heading,
        int headingLevel,
        String text,
        String domSelector) {

    public ScreenRegion {
        Objects.requireNonNull(id, "id");
        role = role == null ? RegionRole.OTHER : role;
        text = text == null ? "" : text;
    }

    public static ScreenRegion of(String id, RegionRole role, String text) {
        return new ScreenRegion(id, role, null, 0, text, null);
    }
}
