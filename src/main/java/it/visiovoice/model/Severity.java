package it.visiovoice.model;

/** Quanto pesa l'esito di un controllo su un valore inserito. */
public enum Severity {

    /** Va bene: si puo' andare avanti. */
    OK,

    /** Sospetto ma non bloccante: vale la pena risentirlo, non impedisce di procedere. */
    WARNING,

    /** Blocca: se si invia cosi', la procedura non avanza. */
    ERROR
}
