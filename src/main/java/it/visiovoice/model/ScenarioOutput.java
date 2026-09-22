package it.visiovoice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Cio' che il servizio restituisce a fine procedura: il numero di protocollo.
 *
 * <p>Modellato perche' e' il criterio di completamento dell'ultimo passo, e quindi la
 * definizione di "ce l'ha fatta". Una procedura che finisce senza che l'utente senta il
 * proprio protocollo non e' finita.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScenarioOutput(
        String id,
        String label,
        String format,
        String example,
        String note) {
}
