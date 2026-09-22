package it.visiovoice.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Le famiglie di barriera che sappiamo riconoscere e riparare.
 *
 * <p>Enumerate e non testo libero: il testo libero produce cinque nomi per la stessa cosa fra
 * chi rileva la barriera e chi la ripara, e la riparazione non scatta mai. Ogni valore e'
 * agganciato al criterio WCAG che descrive il problema, cosi' la scelta e' verificabile.
 */
public enum BarrierType {

    /** Dati significativi disponibili solo come immagine raster. WCAG 1.1.1. */
    IMAGE_ONLY_DATA("dati disponibili solo come immagine"),

    /** Campo di input senza nome accessibile. WCAG 1.3.1, 4.1.2. */
    UNLABELED_INPUT("campo senza etichetta"),

    /** Stato dell'interfaccia comunicato solo per via visiva. WCAG 1.1.1, 1.3.1, 4.1.2. */
    VISUAL_ONLY_STATE("stato visibile solo a chi guarda"),

    /** Errore segnalato senza annuncio allo screen reader. WCAG 1.4.1, 3.3.1. */
    ERROR_NOT_ANNOUNCED("errore non annunciato"),

    /** Comando raggiungibile solo col mouse. WCAG 2.1.1. */
    KEYBOARD_INACCESSIBLE("non raggiungibile da tastiera"),

    /** Struttura della pagina priva di intestazioni o punti di riferimento. WCAG 1.3.1, 2.4.1. */
    MISSING_STRUCTURE("struttura non navigabile"),

    /** Ordine di lettura o di tabulazione incoerente col significato. WCAG 1.3.2, 2.4.3. */
    ILLOGICAL_ORDER("ordine di lettura incoerente"),

    /**
     * Barriera riconosciuta come tale ma non classificata. Esiste perche' uno scenario scritto
     * domani con un tipo nuovo deve continuare a caricarsi: un caricamento che fallisce spegne
     * la demo, un valore sconosciuto la fa solo parlare in modo piu' generico.
     */
    @JsonEnumDefaultValue
    UNKNOWN("barriera non classificata");

    private final String italianLabel;

    BarrierType(String italianLabel) {
        this.italianLabel = italianLabel;
    }

    /** Etichetta pronunciabile, usata per raccontare all'utente cosa era rotto nella pagina. */
    public String italianLabel() {
        return italianLabel;
    }
}
