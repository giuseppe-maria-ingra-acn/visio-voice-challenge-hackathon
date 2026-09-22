package it.visiovoice.model;

/**
 * Quanto racconto ne vuole l'utente adesso.
 *
 * <p>Esiste perche' la stessa schermata serve due bisogni diversi: la prima volta va capita,
 * la seconda va attraversata. Un racconto di lunghezza fissa e' troppo lungo per chi torna e
 * troppo corto per chi arriva.
 */
public enum DetailLevel {

    /** L'essenziale: dove sono e cosa devo fare adesso. */
    BRIEF("sintetico"),

    /** Il racconto normale della schermata. */
    STANDARD("normale"),

    /** Tutto, comprese le riparazioni delle barriere e i riferimenti alle fonti. */
    FULL("completo");

    private final String italianLabel;

    DetailLevel(String italianLabel) {
        this.italianLabel = italianLabel;
    }

    /** Etichetta pronunciabile, usata quando l'utente cambia livello e va confermato. */
    public String italianLabel() {
        return italianLabel;
    }
}
