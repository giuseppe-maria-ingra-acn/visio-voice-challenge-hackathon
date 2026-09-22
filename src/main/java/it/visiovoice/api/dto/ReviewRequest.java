package it.visiovoice.api.dto;

import java.util.Map;

/**
 * I valori correnti del form, letti dal DOM per il riepilogo prima dell'invio.
 *
 * <p>Arrivano nella richiesta e non dallo stato di sessione perche' la pagina e' la fonte di
 * verita'. Un riepilogo costruito su una nostra copia potrebbe descrivere un form diverso da
 * quello che l'utente sta per inviare, e lui non avrebbe modo di accorgersene: e' precisamente
 * il tipo di errore contro cui esiste tutto il resto del progetto.
 *
 * @param values id del campo verso il valore attualmente scritto nel campo
 */
public record ReviewRequest(
        String sessionId,
        Map<String, String> values) {
}
