package it.visiovoice.model;

import java.util.Objects;

/**
 * Un token che il gate non ha ritrovato nella fonte, con abbastanza contesto per andare a
 * guardare a mano.
 *
 * <p>Non e' un messaggio d'errore: e' una riga della mappa del contributo AI. Ogni voce qui
 * dentro e' un punto in cui il sistema ha detto qualcosa che nessuna fonte conferma, e su cui
 * quindi serve un occhio umano.
 *
 * @param token      il token come compariva nel testo
 * @param normalized la forma normalizzata su cui e' stato fatto il confronto: serve a capire
 *                   in dieci secondi se il problema e' un numero inventato o una
 *                   normalizzazione che non ha funzionato
 */
public record UnanchoredToken(
        String segmentId,
        TokenKind kind,
        String token,
        String normalized) {

    public UnanchoredToken {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(token, "token");
    }

    /** Riga leggibile per la mappa del contributo AI. */
    public String describe() {
        return kind + " '" + token + "' (normalizzato: " + normalized + ") nel segmento " + segmentId;
    }
}
