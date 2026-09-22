package it.visiovoice.api.dto;

import it.visiovoice.model.DetailLevel;

/**
 * Cio' che l'estensione manda quando l'utente arriva su una schermata.
 *
 * @param html l'HTML letto dal DOM <b>vivo</b>, non quello servito dal server: appena c'e' un
 *             po' di JavaScript sono due cose diverse, e quella che lo screen reader legge e'
 *             la prima
 * @param url  l'indirizzo della pagina, per risolvere i riferimenti relativi
 */
public record PerceiveRequest(
        String sessionId,
        String url,
        String html,
        DetailLevel level) {
}
