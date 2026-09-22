package it.visiovoice.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Che tipo di dato chiede un campo.
 *
 * <p>Serve a due cose concrete: scegliere il validatore giusto senza uno switch sul nome del
 * campo, e dire all'utente come si compone il valore prima che lo digiti.
 */
public enum FieldKind {
    TEXT,
    FISCAL_CODE,
    IBAN,
    DATE,
    CURRENCY,
    NUMBER,
    EMAIL,
    PHONE,
    SELECT,
    CHECKBOX,
    RADIO,
    @JsonEnumDefaultValue
    OTHER
}
