package it.visiovoice.api.dto;

/**
 * Un singolo valore letto da un campo della pagina, da giudicare.
 *
 * <p>Un campo alla volta e non tutto il form: l'utente sente il giudizio mentre e' ancora su
 * quel campo, quando correggerlo costa un tasto. Un elenco di errori a fine pagina obbliga chi
 * non vede a ritrovare i campi uno per uno, che e' il motivo per cui oggi si blocca.
 *
 * @param value il valore <b>letto in questo istante</b> dal DOM. Il backend non ne tiene copia
 */
public record ValidateRequest(
        String sessionId,
        String fieldId,
        String value) {
}
