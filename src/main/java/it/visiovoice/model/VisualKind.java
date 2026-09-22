package it.visiovoice.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Cosa porta un'immagine, e quindi cosa va ricostruito al suo posto.
 *
 * <p>La distinzione che conta e' fra DECORATIVE e tutto il resto: un'immagine decorativa si
 * tace, una che porta dati va tradotta in struttura vera.
 */
public enum VisualKind {

    /** Una tabella di dati appiattita in un PNG: va ricostruita come tabella. */
    DATA_TABLE,

    /** Indicatore di avanzamento di una procedura a passi. */
    STEP_INDICATOR,

    /** Grafico o diagramma: va riassunto a parole. */
    CHART,

    /** Icona con un significato funzionale. */
    ICON,

    /** Fotografia o illustrazione di contesto. */
    PHOTO,

    /** Nessuna informazione: l'unica cosa giusta da fare e' non dire nulla. */
    DECORATIVE,

    @JsonEnumDefaultValue
    UNKNOWN
}
