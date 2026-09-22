package it.visiovoice.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/** A cosa serve un pezzo di pagina, una volta ripulito dal markup che lo veste. */
public enum RegionRole {
    HEADER,
    NAV,
    HEADING,
    PARAGRAPH,
    INFO_BOX,
    LIST,
    TABLE,
    FORM,
    FIELDSET,
    IMAGE,
    ACTION,
    FOOTER,
    @JsonEnumDefaultValue
    OTHER
}
