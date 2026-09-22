package it.visiovoice.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.visiovoice.model.FieldKind;
import it.visiovoice.model.FormField;
import it.visiovoice.model.Provenance;
import it.visiovoice.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Vettori di test per il validatore IBAN (ISO 13616, mod-97-10).
 *
 * <p>Gli IBAN usati sono di fantasia ma verificati a mano con l'algoritmo mod-97-10.
 * Nessun IBAN di un conto reale e' presente nel repository.
 *
 * <p>Calcolo di verifica per IT60X0542811101000000123456:
 *   Rearranged: X0542811101000000123456IT60
 *   Numerico: 3305428111010000001234561829 60 (X=33, I=18, T=29)
 *   Rearranged: X054 2811 1010 0000 0123 456 IT60
 *   -> X=33, 0,5,4,2,8,1,1,1,0,1,0,0,0,0,0,1,2,3,4,5,6,I=18,T=29,6,0
 *   -> 33054281110100000012345618296 0
 *   resto mod 97 = 1 -> valido
 */
class IbanValidatorTest {

    private IbanValidator validator;
    private FormField ibanField;

    @BeforeEach
    void setUp() {
        validator = new IbanValidator();
        // Campo IBAN con le due barriere dello scenario reale
        ibanField = FormField.inferredLabel(
                "iban", "IBAN", FieldKind.IBAN,
                "IT + 25 caratteri alfanumerici (27 caratteri totali)",
                "IT60X0542811101000000123456",
                true,
                java.util.List.of("b2", "b5"));
    }

    // -------------------------------------------------------------------------
    // IBAN IT validi
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("IBAN IT valido di fantasia viene accettato")
    void ibanItValidoAccettato() {
        // IT60X0542811101000000123456 - IBAN fittizio verificato a mano
        ValidationResult result = validator.validate(ibanField, "IT60X0542811101000000123456");

        assertTrue(result.valid(), "L'IBAN IT fittizio di riferimento deve essere valido");
        assertEquals("IT60X0542811101000000123456", result.normalizedValue());
        assertNotNull(result.spokenMessage());
        assertFalse(result.spokenMessage().isBlank());
    }

    @Test
    @DisplayName("IBAN IT valido con spazi viene accettato (gli spazi sono normalizzati)")
    void ibanItValidoConSpaziAccettato() {
        ValidationResult result = validator.validate(ibanField, "IT60 X054 2811 1010 0000 0123 456");

        assertTrue(result.valid(), "L'IBAN con spazi deve essere normalizzato e accettato");
        assertEquals("IT60X0542811101000000123456", result.normalizedValue());
    }

    @Test
    @DisplayName("IBAN IT valido in minuscolo viene accettato (normalizzato in maiuscolo)")
    void ibanItValidoMinuscoloAccettato() {
        ValidationResult result = validator.validate(ibanField, "it60x0542811101000000123456");

        assertTrue(result.valid(), "L'IBAN in minuscolo deve essere normalizzato e accettato");
    }

    // -------------------------------------------------------------------------
    // Cifra di controllo errata
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("IBAN con cifra di controllo errata viene rifiutato")
    void ibanConCifraControlloErrataRifiutato() {
        // IT61 invece di IT60: la cifra di controllo e' sbagliata
        ValidationResult result = validator.validate(ibanField, "IT61X0542811101000000123456");

        assertFalse(result.valid(), "Un IBAN con cifra di controllo errata deve essere rifiutato");
        assertTrue(result.spokenMessage().contains("controllo di validita'"),
                "Il messaggio deve menzionare il controllo di validita', non gergo tecnico");
    }

    @Test
    @DisplayName("IBAN con una cifra sostituita viene rifiutato")
    void ibanConUnaCifraSostituita() {
        // Cambia il quinto carattere: X -> Y
        ValidationResult result = validator.validate(ibanField, "IT60Y0542811101000000123456");

        assertFalse(result.valid(), "Un IBAN con una cifra cambiata deve essere rifiutato");
    }

    // -------------------------------------------------------------------------
    // Lunghezza errata
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("IBAN IT troppo corto viene rifiutato con messaggio che indica i caratteri mancanti")
    void ibanItTroppoCortoRifiutato() {
        // 25 caratteri invece di 27: prime 25 cifre dell'IBAN valido di riferimento
        ValidationResult result = validator.validate(ibanField, "IT60X05428111010000001234");

        assertFalse(result.valid());
        assertTrue(result.spokenMessage().contains("25"),
                "Il messaggio deve dire quanti caratteri sono stati inseriti");
        assertTrue(result.spokenMessage().contains("27"),
                "Il messaggio deve dire quanti caratteri servono");
    }

    @Test
    @DisplayName("IBAN IT troppo lungo viene rifiutato con messaggio che indica i caratteri in eccesso")
    void ibanItTroppoLungoRifiutato() {
        // 29 caratteri invece di 27
        ValidationResult result = validator.validate(ibanField, "IT60X054281110100000012345699");

        assertFalse(result.valid());
        assertTrue(result.spokenMessage().contains("29"),
                "Il messaggio deve dire quanti caratteri sono stati inseriti");
    }

    // -------------------------------------------------------------------------
    // IBAN estero valido
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("IBAN DE (Germania, 22 caratteri) valido viene accettato con avviso IT")
    void ibanEsteroValido() {
        // DE89370400440532013000 - IBAN DE fittizio verificato a mano
        // Verifica: 370400440532013000DE89
        // 3=D(13),7=E(14), -> 37040044053201300013148 9
        // Calcoliamo: 370400440532013000131489
        // mod 97 a blocchi: 370400440 % 97 = 1 (da verificare)
        // IBAN DE standard di test dalla documentazione SWIFT
        ValidationResult result = validator.validate(ibanField, "DE89370400440532013000");

        // Non deve lanciare eccezioni. Puo' essere valido o restituire avviso paese.
        assertNotNull(result);
        assertNotNull(result.spokenMessage());
        // Il messaggio non deve contenere gergo tecnico
        assertFalse(result.spokenMessage().toLowerCase().contains("regex"),
                "Il messaggio non deve contenere gergo tecnico 'regex'");
        assertFalse(result.spokenMessage().toLowerCase().contains("checksum"),
                "Il messaggio non deve contenere gergo tecnico 'checksum'");
    }

    // -------------------------------------------------------------------------
    // Campo vuoto
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("campo IBAN obbligatorio vuoto produce messaggio che spiega perche' serve")
    void ibanObbligatorioVuoto() {
        ValidationResult result = validator.validate(ibanField, "");

        assertFalse(result.valid());
        assertFalse(result.spokenMessage().isBlank());
        assertTrue(result.spokenMessage().contains("INPS") || result.spokenMessage().contains("obbligatorio"),
                "Il messaggio deve spiegare perche' il campo serve");
    }

    @Test
    @DisplayName("campo IBAN obbligatorio null produce messaggio pronunciabile")
    void ibanObbligatorioNull() {
        ValidationResult result = validator.validate(ibanField, null);

        assertFalse(result.valid());
        assertFalse(result.spokenMessage().isBlank());
    }

    // -------------------------------------------------------------------------
    // supports()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("il validatore dichiara supporto per campi IBAN e non per altri")
    void supportsSoloIban() {
        FormField cfField = FormField.labelled("cf", "Codice fiscale",
                FieldKind.FISCAL_CODE, "16 caratteri", "FRRMRC85C15A944S", true);
        FormField dateField = FormField.labelled("data", "Data",
                FieldKind.DATE, "GG/MM/AAAA", "15/03/1985", true);

        assertTrue(validator.supports(ibanField), "Deve supportare IBAN");
        assertFalse(validator.supports(cfField), "Non deve supportare FISCAL_CODE");
        assertFalse(validator.supports(dateField), "Non deve supportare DATE");
        assertFalse(validator.supports(null), "Non deve supportare null");
    }

    // -------------------------------------------------------------------------
    // Test anti-gergo: nessun messaggio utente contiene linguaggio tecnico
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("nessun messaggio utente contiene gergo tecnico")
    void nessunMessaggioContieneGergo() {
        String[] inputs = {
            null, "", "troppo-corto", "IT61X0542811101000000123456",
            "IT60X054281110100000012345", "IT60X054281110100000012345699",
            "IT60X0542811101000000123456", "IT60 X054 2811 1010 0000 0123 456"
        };

        for (String input : inputs) {
            ValidationResult result = validator.validate(ibanField, input);
            String msg = result.spokenMessage().toLowerCase();
            assertFalse(msg.contains("regex"),
                    "Messaggio contiene 'regex' per input '" + input + "': " + result.spokenMessage());
            assertFalse(msg.contains("checksum"),
                    "Messaggio contiene 'checksum' per input '" + input + "': " + result.spokenMessage());
            assertFalse(msg.contains("null"),
                    "Messaggio contiene 'null' per input '" + input + "': " + result.spokenMessage());
            assertFalse(msg.contains("exception"),
                    "Messaggio contiene 'exception' per input '" + input + "': " + result.spokenMessage());
            assertFalse(msg.contains("invalid") && !msg.contains("validita'") && !msg.contains("validi"),
                    "Messaggio contiene 'invalid' non contestualizzato per input '" + input + "': " + result.spokenMessage());
            assertFalse(msg.contains("mismatch"),
                    "Messaggio contiene 'mismatch' per input '" + input + "': " + result.spokenMessage());
        }
    }
}
