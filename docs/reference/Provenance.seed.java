package it.visiovoice.model;

/**
 * Da dove viene un'informazione che l'utente ascolta.
 *
 * <p>Ogni frase pronunciata da VisioVoice porta con se' la sua origine. Questo non e' un
 * dettaglio di logging: e' un requisito funzionale. L'utente non vede lo schermo, quindi non
 * puo' controllare se cio' che sente corrisponde alla pagina. L'unica difesa che gli resta e'
 * che il sistema sia esplicito su cosa ha letto e cosa ha dedotto.
 *
 * <p>L'ordinale e' significativo: cresce con la distanza dalla fonte. {@link #rank()} permette
 * di confrontare due provenance e tenere la peggiore quando si compone del testo.
 */
public enum Provenance {

    /** Testo copiato letteralmente dalla pagina. Zero rielaborazione, zero rischio. */
    SOURCE_VERBATIM("letto dalla pagina", false),

    /**
     * Testo della pagina riformulato per essere piu' chiaro, con lo stesso significato.
     * Ogni numero e ogni nome proprio sono stati ritrovati nella fonte da {@code FidelityAgent}.
     */
    AI_REPHRASED("riformulato dall'AI, verificato sulla fonte", false),

    /**
     * Informazione che l'AI ha dedotto e che NON e' letteralmente nella pagina: un calcolo,
     * un collegamento fra due passi, una spiegazione di contesto. Puo' essere corretta, ma
     * nessun controllo automatico lo garantisce.
     */
    AI_INFERRED("dedotto dall'AI, da verificare", true),

    /** Un essere umano ha letto e approvato questo testo. La provenance piu' forte che esista. */
    HUMAN_REVIEWED("verificato da una persona", false);

    private final String italianLabel;
    private final boolean needsHumanReview;

    Provenance(String italianLabel, boolean needsHumanReview) {
        this.italianLabel = italianLabel;
        this.needsHumanReview = needsHumanReview;
    }

    /** Etichetta pronunciabile, letta all'utente quando chiede "da dove viene questa cosa?". */
    public String italianLabel() {
        return italianLabel;
    }

    /** Se true l'interfaccia deve segnalarlo e la demo deve mostrarlo come punto di revisione umana. */
    public boolean needsHumanReview() {
        return needsHumanReview;
    }

    /** Affidabilita' decrescente: 0 = fonte diretta. Usato per comporre segmenti. */
    public int rank() {
        return switch (this) {
            case HUMAN_REVIEWED -> 0;
            case SOURCE_VERBATIM -> 1;
            case AI_REPHRASED -> 2;
            case AI_INFERRED -> 3;
        };
    }

    /**
     * Quando si concatena testo di origini diverse, il risultato vale quanto il suo anello
     * piu' debole. Unire una frase letta dalla pagina a una dedotta produce testo dedotto:
     * non si puo' rivendicare la solidita' della parte migliore.
     */
    public static Provenance weakest(Provenance a, Provenance b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.rank() >= b.rank() ? a : b;
    }
}
