package it.visiovoice.model;

/** Che rapporto ha con il servizio vero la pagina su cui stiamo lavorando. */
public enum ServiceFidelity {

    /** Il portale vero, raggiunto in rete. */
    LIVE,

    /** Una replica locale che riproduce struttura e barriere del portale vero. */
    REPLICA,

    /** Una pagina di comodo, utile solo come rete di sicurezza della demo. */
    MOCKUP
}
