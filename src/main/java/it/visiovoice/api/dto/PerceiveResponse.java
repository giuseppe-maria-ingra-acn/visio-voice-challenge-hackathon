package it.visiovoice.api.dto;

import it.visiovoice.model.FidelityReport;
import it.visiovoice.model.ScreenModel;
import it.visiovoice.model.SpokenScript;
import it.visiovoice.model.VisualDescription;

import java.util.List;

/**
 * Cio' che il livello accessibile riceve dopo aver letto una schermata.
 *
 * <p>{@code visuals} e' il canale attraverso cui la tabella chiusa in un'immagine arriva al
 * browser come dati. Senza di esso il frontend non ha modo di ottenerla: la narrazione
 * racconta la schermata, ma le righe e le colonne da cui costruire un {@code <table>} vero
 * stanno solo nel {@code VisualDescription}. E' il momento centrale del prodotto, quindi
 * merita un campo suo invece di essere cercato dentro altre strutture.
 */
public record PerceiveResponse(
        String sessionId,
        ScreenModel screen,
        SpokenScript script,
        FidelityReport fidelity,
        List<VisualDescription> visuals) {

    public PerceiveResponse {
        visuals = visuals == null ? List.of() : List.copyOf(visuals);
    }

    /** Forma ridotta per gli endpoint che restituiscono solo un racconto già memorizzato. */
    public PerceiveResponse(String sessionId, ScreenModel screen, SpokenScript script,
                            FidelityReport fidelity) {
        this(sessionId, screen, script, fidelity, List.of());
    }
}
