package it.visiovoice.api.dto;

/**
 * Un errore, detto in modo che si possa leggere a voce alta.
 *
 * <p>{@code spokenMessage} non e' il messaggio dell'eccezione: quello parla di classi Java e
 * chi ascolta non puo' farci niente. Qui ci va cosa non e' andato e cosa si puo' fare adesso.
 * {@code code} resta per chi guarda i log.
 */
public record ErrorResponse(String code, String spokenMessage) {

    public static ErrorResponse of(String code, String spokenMessage) {
        return new ErrorResponse(code, spokenMessage);
    }
}
