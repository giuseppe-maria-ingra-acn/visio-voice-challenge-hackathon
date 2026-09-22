package it.visiovoice.validation;

import it.visiovoice.agents.ValidationPort;
import it.visiovoice.model.FieldKind;
import it.visiovoice.model.FormField;
import it.visiovoice.model.ValidationResult;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import org.springframework.stereotype.Service;

/**
 * Validatore date con {@link ResolverStyle#STRICT}: il 31 febbraio viene rifiutato.
 *
 * <p>Rifiuta anche le date impossibili nel contesto, come una data di nascita nel futuro.
 */
@Service
public class DateValidator implements ValidationPort {

    /** Formato atteso: GG/MM/AAAA. Il ResolverStyle STRICT rifiuta date come il 31/02. */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    @Override
    public boolean supports(FormField field) {
        return field != null && field.kind() == FieldKind.DATE;
    }

    @Override
    public ValidationResult validate(FormField field, String userInput) {
        String fieldId = field != null ? field.id() : null;

        if (userInput == null || userInput.isBlank()) {
            if (field != null && field.required()) {
                return ValidationResult.invalid(fieldId,
                        "Questo campo data e' obbligatorio. Inserisci la data nel formato "
                        + "giorno/mese/anno, per esempio 15/03/1985.");
            }
            return ValidationResult.valid(fieldId,
                    "Il campo data e' vuoto.", null);
        }

        String input = userInput.trim();

        LocalDate date;
        try {
            date = LocalDate.parse(input, FORMATTER);
        } catch (DateTimeParseException e) {
            return ValidationResult.invalid(fieldId,
                    "La data inserita non e' valida. Usa il formato giorno/mese/anno con due "
                    + "cifre per il giorno e il mese e quattro per l'anno, per esempio 15/03/1985. "
                    + "Controlla anche che il giorno esista nel mese: febbraio ha al massimo 29 giorni.");
        }

        LocalDate today = LocalDate.now();

        // Una data di nascita nel futuro e' impossibile
        boolean isBirthDate = fieldId != null && (fieldId.contains("nascita") || fieldId.contains("birth"));
        if (isBirthDate && date.isAfter(today)) {
            return ValidationResult.invalid(fieldId,
                    "La data di nascita non puo' essere nel futuro. La data inserita, "
                    + input + ", e' successiva a oggi. Controlla di aver scritto l'anno corretto.");
        }

        // Una data di nascita troppo lontana nel passato e' sospetta (piu' di 130 anni fa)
        if (isBirthDate && date.isBefore(today.minusYears(130))) {
            return ValidationResult.warning(fieldId,
                    "La data di nascita " + input + " e' molto lontana nel passato: "
                    + "controlla di aver scritto l'anno corretto.",
                    input);
        }

        return ValidationResult.valid(fieldId,
                "Data registrata: " + formatDate(date) + ".",
                input);
    }

    private String formatDate(LocalDate date) {
        return date.getDayOfMonth() + " "
                + italianMonth(date.getMonthValue()) + " "
                + date.getYear();
    }

    private String italianMonth(int month) {
        return switch (month) {
            case 1 -> "gennaio";
            case 2 -> "febbraio";
            case 3 -> "marzo";
            case 4 -> "aprile";
            case 5 -> "maggio";
            case 6 -> "giugno";
            case 7 -> "luglio";
            case 8 -> "agosto";
            case 9 -> "settembre";
            case 10 -> "ottobre";
            case 11 -> "novembre";
            case 12 -> "dicembre";
            default -> String.valueOf(month);
        };
    }
}
