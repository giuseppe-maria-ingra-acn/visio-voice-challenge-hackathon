package it.visiovoice.model;

import java.util.List;
import java.util.Objects;

/**
 * L'esito dell'ancoraggio di un singolo segmento alla sua fonte.
 *
 * @param anchored  true se ogni numero e ogni nome proprio del testo si ritrova nella fonte
 * @param effective la provenance che il segmento <b>merita</b>, che puo' essere piu' debole di
 *                  quella che dichiarava
 */
public record AnchorCheck(
        boolean anchored,
        Provenance effective,
        List<UnanchoredToken> unanchored) {

    public AnchorCheck {
        Objects.requireNonNull(effective, "effective");
        unanchored = List.copyOf(unanchored == null ? List.of() : unanchored);
    }

    static AnchorCheck ok(Provenance provenance) {
        return new AnchorCheck(true, provenance, List.of());
    }
}
