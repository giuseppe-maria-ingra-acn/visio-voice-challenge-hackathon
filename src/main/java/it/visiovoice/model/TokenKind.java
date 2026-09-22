package it.visiovoice.model;

/** Che genere di token il gate non e' riuscito a ritrovare nella fonte. */
public enum TokenKind {

    /** Una cifra: e' il caso che fa prendere una decisione sbagliata a chi non puo' controllare. */
    NUMBER,

    /** Un nome proprio: un ente, una citta', una persona, il numero di una circolare. */
    PROPER_NOUN
}
