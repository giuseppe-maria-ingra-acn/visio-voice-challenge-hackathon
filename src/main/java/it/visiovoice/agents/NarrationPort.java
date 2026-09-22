package it.visiovoice.agents;

import it.visiovoice.model.DetailLevel;
import it.visiovoice.model.ScreenModel;
import it.visiovoice.model.SpokenScript;
import it.visiovoice.model.VisualAsset;
import it.visiovoice.model.VisualDescription;

/**
 * Da {@link ScreenModel} a racconto. Lo implementa {@code narration}.
 *
 * <p>E' l'unico strato che usa un modello generativo, e quindi l'unico i cui prodotti passano
 * obbligatoriamente da {@link it.visiovoice.model.ProvenanceGate} prima di raggiungere
 * l'utente. L'implementazione non deve restituire uno script che non ha passato il gate.
 */
public interface NarrationPort {

    /** Il racconto della schermata, al livello di dettaglio chiesto, gate compreso. */
    SpokenScript narrate(ScreenModel screen, DetailLevel level, AgentContext ctx);

    /** Cosa c'e' dentro un'immagine, per chi non la vede. */
    VisualDescription describeVisual(VisualAsset asset, AgentContext ctx);
}
