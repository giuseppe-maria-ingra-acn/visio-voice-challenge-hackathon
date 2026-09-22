package it.visiovoice.model;

/** Dove siamo nel percorso dell'utente. Nomina i suoi momenti, non i nostri componenti. */
public enum Phase {

    /** Sessione aperta, ancora nessun racconto. */
    INTRO,

    /** Stiamo raccontando cosa c'e' sulla schermata. */
    NARRATE,

    /** Stiamo accompagnando campo per campo dentro la procedura. */
    GUIDE,

    /** Riepilogo prima dell'invio. */
    REVIEW,

    /** Inviato: c'e' un protocollo. */
    DONE
}
