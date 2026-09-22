package it.visiovoice.model;

/**
 * Che lavoro fa un segmento nel racconto.
 *
 * <p>Il ruolo non e' decorazione: guida l'ordine di lettura, decide cosa si puo' saltare al
 * livello di dettaglio sintetico, e permette all'interfaccia di instradare un segmento nella
 * regione aria-live giusta invece di leggere tutto di seguito.
 */
public enum SegmentRole {

    /** Dove sono: titolo della pagina o del passo. */
    TITLE,

    /** Cosa c'e' qui, in due frasi. */
    OVERVIEW,

    /** Lo stato della procedura: a che passo siamo, quanti restano. */
    STEP_STATUS,

    /** Un dato letto dalla pagina: un importo, una soglia, una scadenza. */
    DATA,

    /** La ricostruzione di cio' che una barriera nascondeva. */
    BARRIER_REPAIR,

    /** Cosa devo scrivere in questo campo. */
    FIELD_PROMPT,

    /** Come muoversi: quali tasti, dove porta questo pulsante. */
    NAVIGATION,

    /** Un avviso: qualcosa richiede attenzione ma non blocca. */
    WARNING,

    /** Un errore che blocca l'avanzamento. */
    ERROR,

    /** Il riepilogo prima dell'invio. */
    REVIEW,

    /** La conferma dopo l'invio, protocollo compreso. */
    CONFIRMATION,

    /** Aiuto su richiesta, compresa la risposta alla domanda sulla provenienza. */
    HELP
}
