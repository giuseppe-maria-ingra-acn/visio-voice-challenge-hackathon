package it.visiovoice.model;

/**
 * Quanto siamo vicini alla realta' su una singola prova: una barriera, un fatto.
 *
 * <p>Vive a livello di singolo elemento e non solo di servizio, perche' in uno stesso scenario
 * convivono barriere viste con i propri occhi su un portale vero e barriere ricostruite nella
 * replica a partire da un pattern documentato. Dichiararlo per elemento evita che la fedelta'
 * del piu' debole contamini il racconto di tutto il resto.
 *
 * <p>I nomi sono in italiano perche' questi valori li scrive a mano {@code scenario-researcher}
 * dentro {@code resources/scenarios}, accanto alle citazioni delle fonti.
 */
public enum EvidenceFidelity {

    /** Verificato direttamente sul servizio reale: qualcuno ha guardato quella pagina. */
    OSSERVATO("osservato sul servizio reale"),

    /** Riprodotto nella replica a partire da un pattern documentato, non visto su quella pagina. */
    RICOSTRUITO("ricostruito da un pattern documentato");

    private final String italianLabel;

    EvidenceFidelity(String italianLabel) {
        this.italianLabel = italianLabel;
    }

    /** Etichetta pronunciabile, usata quando l'utente chiede la provenienza di una barriera. */
    public String italianLabel() {
        return italianLabel;
    }
}
