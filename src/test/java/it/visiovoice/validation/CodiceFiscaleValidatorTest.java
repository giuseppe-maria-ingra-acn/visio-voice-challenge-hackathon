package it.visiovoice.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.visiovoice.model.FieldKind;
import it.visiovoice.model.FormField;
import it.visiovoice.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Vettori di test per il validatore Codice Fiscale (DM 23/12/1976).
 *
 * <p>Tutti i codici fiscali usati sono di fantasia, verificati a mano con le tabelle del
 * DM. Nessun codice fiscale di una persona reale e' presente nel repository.
 *
 * <h2>Codici fiscali fittizi verificati a mano</h2>
 *
 * <h3>TSTPRN00A01Z999E — valido</h3>
 * Sequenza: T S T P R N 0 0 A 0 1 Z 9 9 9
 * Tabelle DM 23/12/1976 (1-based, dispari=ODD, pari=EVEN):
 *   pos1 T(odd)=14, pos2 S(even)=18, pos3 T(odd)=14, pos4 P(even)=15
 *   pos5 R(odd)=8,  pos6 N(even)=13, pos7 0(odd)=1,  pos8 0(even)=0
 *   pos9 A(odd)=1,  pos10 0(even)=0, pos11 1(odd)=0, pos12 Z(even)=25
 *   pos13 9(odd)=21, pos14 9(even)=9, pos15 9(odd)=21
 * Somma = 14+18+14+15+8+13+1+0+1+0+0+25+21+9+21 = 160
 * 160 % 26 = 4 -> 'A'+4 = 'E' -> carattere di controllo: E
 *
 * <h3>TSTPRNL0A01Z999E — omocodico valido</h3>
 * Come TSTPRN00A01Z999E ma con 0 in posizione 7 sostituito da L (omocodia: 0->L).
 * Il carattere di controllo resta E perche' la tabella dispari mappa L-come-0 al valore 1.
 */
class CodiceFiscaleValidatorTest {

    private CodiceFiscaleValidator validator;
    private FormField cfField;

    @BeforeEach
    void setUp() {
        validator = new CodiceFiscaleValidator();
        cfField = FormField.labelled("cf_richiedente", "Codice fiscale",
                FieldKind.FISCAL_CODE, "16 caratteri alfanumerici", "TSTPRN00A01Z999E", true);
    }

    // -------------------------------------------------------------------------
    // Codici fiscali validi
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("codice fiscale fittizio verificato a mano viene accettato")
    void codiceFiscaleValidoAccettato() {
        ValidationResult result = validator.validate(cfField, "TSTPRN00A01Z999E");

        assertTrue(result.valid(), "TSTPRN00A01Z999E deve essere valido");
        assertEquals("TSTPRN00A01Z999E", result.normalizedValue());
        assertNotNull(result.spokenMessage());
        assertFalse(result.spokenMessage().isBlank());
    }

    @Test
    @DisplayName("codice fiscale in minuscolo viene normalizzato e accettato")
    void codiceFiscaleMinuscoloNormalizzato() {
        ValidationResult result = validator.validate(cfField, "tstprn00a01z999e");

        assertTrue(result.valid(), "Il codice in minuscolo deve essere normalizzato e accettato");
        assertEquals("TSTPRN00A01Z999E", result.normalizedValue());
    }

    @Test
    @DisplayName("codice fiscale con spazi viene normalizzato e accettato")
    void codiceFiscaleConSpaziNormalizzato() {
        ValidationResult result = validator.validate(cfField, "TSTPRN 00A01Z 999E");

        assertTrue(result.valid(), "Il codice con spazi deve essere normalizzato e accettato");
    }

    // -------------------------------------------------------------------------
    // Omocodia
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("codice fiscale omocodico (cifra sostituita da lettera) viene accettato")
    void codiceFiscaleOmocodicoAccettato() {
        // TSTPRNL0A01Z999E: L sostituisce 0 in posizione 7 (omocodia, 0->L)
        // Il controllo E rimane invariato perche' L-come-0 contribuisce lo stesso valore.
        ValidationResult result = validator.validate(cfField, "TSTPRNL0A01Z999E");

        assertTrue(result.valid(),
                "Un CF omocodico con L al posto di 0 deve essere valido: "
                + "la tabella dispari mappa L->0->1, stesso valore del carattere originale");
    }

    // -------------------------------------------------------------------------
    // Carattere di controllo errato
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("codice fiscale con carattere di controllo sbagliato viene rifiutato")
    void codiceFiscaleControlloErratoRifiutato() {
        // TSTPRN00A01Z999X: X non e' il carattere di controllo corretto (E)
        ValidationResult result = validator.validate(cfField, "TSTPRN00A01Z999X");

        assertFalse(result.valid(), "Un CF con carattere di controllo errato deve essere rifiutato");
        assertTrue(result.spokenMessage().contains("E") || result.spokenMessage().contains("controllo"),
                "Il messaggio deve indicare il carattere atteso o menzionare il controllo");
    }

    @Test
    @DisplayName("il messaggio per carattere di controllo errato indica il carattere atteso")
    void messaggioControllo() {
        ValidationResult result = validator.validate(cfField, "TSTPRN00A01Z999X");

        // Deve dire che il carattere atteso e' E
        String msg = result.spokenMessage();
        assertTrue(msg.contains("E"),
                "Il messaggio deve indicare il carattere corretto: 'E', messaggio: " + msg);
        assertTrue(msg.contains("X"),
                "Il messaggio deve indicare il carattere inserito: 'X', messaggio: " + msg);
    }

    // -------------------------------------------------------------------------
    // Lunghezza errata
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("codice fiscale troppo corto (15 caratteri) viene rifiutato")
    void codiceFiscaleTroppoCortoRifiutato() {
        ValidationResult result = validator.validate(cfField, "TSTPRN00A01Z999");

        assertFalse(result.valid());
        assertTrue(result.spokenMessage().contains("16"),
                "Il messaggio deve citare i 16 caratteri attesi");
        assertTrue(result.spokenMessage().contains("15"),
                "Il messaggio deve dire quanti caratteri sono stati inseriti");
    }

    @Test
    @DisplayName("codice fiscale troppo lungo (17 caratteri) viene rifiutato")
    void codiceFiscaleTroppoLungoRifiutato() {
        ValidationResult result = validator.validate(cfField, "TSTPRN00A01Z999EZ");

        assertFalse(result.valid());
        assertTrue(result.spokenMessage().contains("16"),
                "Il messaggio deve citare i 16 caratteri attesi");
    }

    // -------------------------------------------------------------------------
    // Campo vuoto
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("codice fiscale obbligatorio vuoto produce messaggio che spiega dove trovarlo")
    void codiceFiscaleObbligatorioVuoto() {
        ValidationResult result = validator.validate(cfField, "");

        assertFalse(result.valid());
        assertTrue(result.spokenMessage().contains("tessera") || result.spokenMessage().contains("documento"),
                "Il messaggio deve dire dove trovare il codice fiscale");
    }

    // -------------------------------------------------------------------------
    // supports()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("il validatore dichiara supporto per FISCAL_CODE e non per altri tipi")
    void supportsSoloCF() {
        FormField ibanField = FormField.labelled("iban", "IBAN",
                FieldKind.IBAN, "27 caratteri", "IT60X0542811101000000123456", true);

        assertTrue(validator.supports(cfField), "Deve supportare FISCAL_CODE");
        assertFalse(validator.supports(ibanField), "Non deve supportare IBAN");
        assertFalse(validator.supports(null), "Non deve supportare null");
    }

    // -------------------------------------------------------------------------
    // Test anti-gergo: nessun messaggio utente contiene linguaggio tecnico
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("nessun messaggio utente contiene gergo tecnico")
    void nessunMessaggioContieneGergo() {
        String[] inputs = {
            null, "", "TROPPO-CORTO", "TSTPRN00A01Z999X", "TSTPRN00A01Z999",
            "TSTPRN00A01Z999EZ", "TSTPRN00A01Z999E", "TSTPRNL0A01Z999E"
        };

        for (String input : inputs) {
            ValidationResult result = validator.validate(cfField, input);
            String msg = result.spokenMessage().toLowerCase();
            assertFalse(msg.contains("regex"),
                    "Messaggio contiene 'regex' per input '" + input + "': " + result.spokenMessage());
            assertFalse(msg.contains("checksum"),
                    "Messaggio contiene 'checksum' per input '" + input + "': " + result.spokenMessage());
            assertFalse(msg.contains("null"),
                    "Messaggio contiene 'null' per input '" + input + "': " + result.spokenMessage());
            assertFalse(msg.contains("exception"),
                    "Messaggio contiene 'exception' per input '" + input + "': " + result.spokenMessage());
            assertFalse(msg.contains("mismatch"),
                    "Messaggio contiene 'mismatch' per input '" + input + "': " + result.spokenMessage());
        }
    }
}
