package it.visiovoice.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;

import it.visiovoice.model.FieldKind;
import it.visiovoice.model.FormField;
import it.visiovoice.model.ValidationResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Il test che impedisce al codice di scivolare verso il linguaggio degli sviluppatori.
 *
 * <p>Cerca {@code regex|checksum|null|exception|invalid|mismatch} nei messaggi utente di tutti
 * i validatori e fallisce se li trova. "invalid" e' bannato nelle sue forme inglesi, non quando
 * appare come parte di una parola italiana come "validita'" o "validi".
 *
 * <p>Questo test non e' una cortesia: e' il contratto che garantisce che i messaggi siano
 * pronunciabili da una sintesi vocale e comprensibili da chi non vede lo schermo.
 */
class ValidationMessagesTest {

    private static final String[] JARGON_PATTERNS = {
        "regex", "checksum", "exception", "mismatch",
        "stacktrace", "npe", "nullpointer"
    };

    private final IbanValidator ibanValidator = new IbanValidator();
    private final CodiceFiscaleValidator cfValidator = new CodiceFiscaleValidator();
    private final DateValidator dateValidator = new DateValidator();

    private static final FormField IBAN_FIELD = FormField.inferredLabel(
            "iban", "IBAN", FieldKind.IBAN,
            "IT + 25 caratteri", "IT60X0542811101000000123456",
            true, List.of("b2", "b5"));

    private static final FormField CF_FIELD = FormField.labelled(
            "cf_richiedente", "Codice fiscale", FieldKind.FISCAL_CODE,
            "16 caratteri", "TSTPRN00A01Z999E", true);

    private static final FormField DATE_FIELD = FormField.labelled(
            "data_nascita", "Data di nascita", FieldKind.DATE,
            "GG/MM/AAAA", "15/03/1985", true);

    @Test
    @DisplayName("i messaggi IBAN non contengono gergo tecnico in nessun caso")
    void ibanMessaggiSenzaGergo() {
        String[] testInputs = {
            null, "", "troppo-corto", "IT61X0542811101000000123456",
            "IT60X054281110100000012345", "IT60X054281110100000012345699",
            "IT60X0542811101000000123456", "IT60 X054 2811 1010 0000 0123 456",
            "DE89370400440532013000", "NOTANIBAN123"
        };

        for (String input : testInputs) {
            ValidationResult result = ibanValidator.validate(IBAN_FIELD, input);
            assertNoJargon(result.spokenMessage(), "IBAN", input);
        }
    }

    @Test
    @DisplayName("i messaggi Codice Fiscale non contengono gergo tecnico in nessun caso")
    void cfMessaggiSenzaGergo() {
        String[] testInputs = {
            null, "", "TROPPO-CORTO", "TSTPRN00A01Z999X",
            "TSTPRN00A01Z999", "TSTPRN00A01Z999EZ",
            "TSTPRN00A01Z999E", "TSTPRNL0A01Z999E"
        };

        for (String input : testInputs) {
            ValidationResult result = cfValidator.validate(CF_FIELD, input);
            assertNoJargon(result.spokenMessage(), "CF", input);
        }
    }

    @Test
    @DisplayName("i messaggi Data non contengono gergo tecnico in nessun caso")
    void dateMessaggiSenzaGergo() {
        String[] testInputs = {
            null, "", "non-una-data", "31/02/2000",
            "15/03/1985", "15/03/2099", "15-03-1985", "1985-03-15"
        };

        for (String input : testInputs) {
            ValidationResult result = dateValidator.validate(DATE_FIELD, input);
            assertNoJargon(result.spokenMessage(), "Data", input);
        }
    }

    @Test
    @DisplayName("i messaggi di successo non sono mai vuoti")
    void messaggiSuccessoNonVuoti() {
        ValidationResult ibanOk = ibanValidator.validate(IBAN_FIELD, "IT60X0542811101000000123456");
        ValidationResult cfOk = cfValidator.validate(CF_FIELD, "TSTPRN00A01Z999E");
        ValidationResult dateOk = dateValidator.validate(DATE_FIELD, "15/03/1985");

        assertFalse(ibanOk.spokenMessage().isBlank(),
                "Il messaggio di successo IBAN non deve essere vuoto");
        assertFalse(cfOk.spokenMessage().isBlank(),
                "Il messaggio di successo CF non deve essere vuoto");
        assertFalse(dateOk.spokenMessage().isBlank(),
                "Il messaggio di successo Data non deve essere vuoto");
    }

    private void assertNoJargon(String message, String validatorName, String input) {
        String lower = message.toLowerCase();
        for (String pattern : JARGON_PATTERNS) {
            assertFalse(lower.contains(pattern),
                    validatorName + " per input '" + input + "' contiene gergo '" + pattern
                    + "': " + message);
        }
        // Controlla "null" come parola: "null" in inglese (non parte di "annullare" etc.)
        // Controlla la stringa esatta " null" o "null " o "null." etc.
        assertFalse(lower.matches(".*\\bnull\\b.*"),
                validatorName + " per input '" + input + "' contiene 'null': " + message);
        // Controlla "invalid" in inglese puro (non "validita'", "valido", "invalido")
        assertFalse(lower.matches(".*\\binvalid\\b.*"),
                validatorName + " per input '" + input + "' contiene 'invalid' inglese: " + message);
    }
}
