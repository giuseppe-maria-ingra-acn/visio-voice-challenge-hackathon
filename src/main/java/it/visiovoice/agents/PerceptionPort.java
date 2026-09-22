package it.visiovoice.agents;

import it.visiovoice.model.Barrier;
import it.visiovoice.model.ScreenModel;
import java.util.List;

/**
 * Da HTML a {@link ScreenModel}. Lo implementa {@code perception}, lo chiama l'API.
 *
 * <p>Deterministico e senza modelli generativi: e' la parte del sistema di cui possiamo
 * dimostrare la correttezza su HTML statico. Un campo che qui non c'e' non deve poter comparire
 * piu' a valle.
 */
public interface PerceptionPort {

    /**
     * Legge la pagina.
     *
     * @param html    l'HTML come lo ha letto l'estensione dal DOM vivo, non come lo ha servito
     *                il server: sono due cose diverse appena c'e' un po' di JavaScript
     * @param baseUrl per risolvere i riferimenti relativi delle immagini
     */
    ScreenModel perceive(String html, String baseUrl);

    /**
     * Le barriere presenti in questa schermata.
     *
     * <p>Separato da {@link #perceive} perche' sono due affermazioni verificabili in modo
     * indipendente: "la pagina l'ho letta giusta" e "i problemi li ho riconosciuti".
     */
    List<Barrier> detectBarriers(ScreenModel screen);
}
