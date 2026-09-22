package it.visiovoice.model;

import java.util.Objects;

/**
 * Un'immagine nella pagina, con tutto cio' che si puo' sapere di lei <b>senza</b> guardarla.
 *
 * <p>{@code nearbyText} e {@code src} non sono rumore: sono gli unici indizi verificabili su
 * cosa contiene un PNG. Il nome del file e il paragrafo che la precede dicono spesso piu'
 * dell'attributo {@code alt}, e a differenza della descrizione generata da un modello sono
 * materiale che il gate puo' usare come fonte.
 *
 * @param altText   l'alt come sta nella pagina. Puo' essere vuoto o inutile: e' il problema
 * @param nearbyText il testo che la circonda, usato come contesto e come fonte di ancoraggio
 */
public record VisualAsset(
        String id,
        String src,
        String altText,
        VisualKind kind,
        String nearbyText,
        String domSelector) {

    public VisualAsset {
        Objects.requireNonNull(id, "id");
        kind = kind == null ? VisualKind.UNKNOWN : kind;
        altText = altText == null ? "" : altText;
        nearbyText = nearbyText == null ? "" : nearbyText;
    }

    /** True se l'alt manca o non trasmette nulla: allora la descrizione va ricostruita. */
    public boolean altIsUseless() {
        String normalized = altText.trim().toLowerCase();
        return normalized.isEmpty()
                || normalized.equals("immagine")
                || normalized.equals("image")
                || normalized.equals("grafico")
                || normalized.equals("foto")
                || normalized.equals("logo");
    }
}
