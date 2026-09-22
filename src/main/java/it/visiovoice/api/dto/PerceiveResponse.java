package it.visiovoice.api.dto;

import it.visiovoice.model.FidelityReport;
import it.visiovoice.model.ScreenModel;
import it.visiovoice.model.SpokenScript;

/**
 * Cosa c'e' sulla schermata, come raccontarla, e cosa del racconto non e' verificato.
 *
 * <p>Il rapporto di fidelity viaggia insieme al racconto e non su un canale a parte: se
 * arrivasse separato, un'interfaccia potrebbe pronunciare il testo prima di sapere quali
 * segmenti vanno annunciati come dedotti - e l'annuncio che arriva dopo non serve a nessuno.
 */
public record PerceiveResponse(
        String sessionId,
        ScreenModel screen,
        SpokenScript script,
        FidelityReport fidelity) {
}
