package it.visiovoice.validation;

import it.visiovoice.agents.ValidationPort;
import it.visiovoice.model.FieldKind;
import it.visiovoice.model.FormField;
import it.visiovoice.model.ValidationResult;
import org.springframework.stereotype.Service;

/**
 * Validatore IBAN secondo ISO 13616 (mod-97-10).
 *
 * <p>Algoritmo: sposta i primi 4 caratteri in coda, converti le lettere (A=10..Z=35), calcola
 * il resto modulo 97 a blocchi (non costruendo un BigInteger da 30 cifre). Il risultato deve
 * valere 1. Verifica anche la lunghezza per paese: IT = 27 caratteri.
 *
 * <p>Il messaggio di errore e' una frase pronunciabile: dice cosa e' sbagliato e cosa fare,
 * nella stessa frase. Marco non vede il campo accanto all'errore.
 */
@Service
public class IbanValidator implements ValidationPort {

    /** Lunghezza attesa per IBAN italiani secondo ISO 13616. */
    private static final int IT_IBAN_LENGTH = 27;

    @Override
    public boolean supports(FormField field) {
        return field != null && field.kind() == FieldKind.IBAN;
    }

    @Override
    public ValidationResult validate(FormField field, String userInput) {
        String fieldId = field != null ? field.id() : null;

        if (userInput == null || userInput.isBlank()) {
            if (field != null && field.required()) {
                return ValidationResult.invalid(fieldId,
                        "Questo campo e' obbligatorio: senza l'IBAN l'INPS non puo' accreditare "
                        + "l'Assegno Unico. Inserisci il codice IBAN del tuo conto corrente.");
            }
            return ValidationResult.valid(fieldId,
                    "Il campo IBAN e' vuoto. Se vuoi procedere senza inserirlo, ricordati che "
                    + "il pagamento non potra' essere accreditato.", null);
        }

        // Normalizza: rimuovi spazi e converti in maiuscolo
        String normalized = userInput.replaceAll("\\s+", "").toUpperCase();

        // Controlla il prefisso paese
        if (!normalized.startsWith("IT")) {
            // Potrebbe essere un IBAN estero valido, ma il servizio INPS richiede IBAN italiano
            // Validiamo comunque con mod-97-10 per dare un feedback accurato
            if (normalized.length() < 4) {
                return ValidationResult.invalid(fieldId,
                        "L'IBAN inserito e' troppo corto: ha solo " + normalized.length()
                        + " caratteri. Un IBAN italiano inizia con IT e ha 27 caratteri in totale.");
            }
            // Per IBAN esteri, eseguiamo solo la validazione mod-97-10
            String mod97Error = checkMod97(normalized);
            if (mod97Error != null) {
                return ValidationResult.invalid(fieldId, mod97Error);
            }
            return ValidationResult.valid(fieldId,
                    "L'IBAN inserito e' formalmente valido, ma attenzione: inizia con "
                    + normalized.substring(0, 2) + " invece di IT. "
                    + "Per ricevere i pagamenti INPS serve un conto corrente italiano.",
                    normalized);
        }

        // Controlla la lunghezza per IBAN italiani
        if (normalized.length() != IT_IBAN_LENGTH) {
            int actual = normalized.length();
            int diff = IT_IBAN_LENGTH - actual;
            if (diff > 0) {
                return ValidationResult.invalid(fieldId,
                        "L'IBAN deve avere " + IT_IBAN_LENGTH + " caratteri, tu ne hai scritti "
                        + actual + ". Mancano " + diff + " caratteri: controlla se ne hai omesso qualcuno.");
            } else {
                return ValidationResult.invalid(fieldId,
                        "L'IBAN deve avere " + IT_IBAN_LENGTH + " caratteri, tu ne hai scritti "
                        + actual + ". Ce ne sono " + Math.abs(diff) + " di troppo: "
                        + "controlla se hai inserito spazi o caratteri in eccesso.");
            }
        }

        // Controlla il checksum con mod-97-10
        String mod97Error = checkMod97(normalized);
        if (mod97Error != null) {
            return ValidationResult.invalid(fieldId, mod97Error);
        }

        // IBAN valido: restituisci il valore formattato a gruppi di 4
        String formatted = formatIban(normalized);
        return ValidationResult.valid(fieldId,
                "L'IBAN e' corretto. Ho registrato: " + formatted + ".",
                normalized);
    }

    /**
     * Verifica il checksum mod-97-10 secondo ISO 13616.
     * Restituisce null se l'IBAN e' valido, oppure un messaggio di errore pronunciabile.
     */
    private String checkMod97(String iban) {
        // Verifica che l'IBAN contenga solo caratteri alfanumerici
        if (!iban.matches("[A-Z0-9]+")) {
            return "L'IBAN contiene caratteri non validi: deve essere composto solo da lettere "
                    + "maiuscole e cifre. Controlla se hai inserito simboli come trattini o punti.";
        }

        // Sposta i primi 4 caratteri in coda: IBAN[4..] + IBAN[0..3]
        String rearranged = iban.substring(4) + iban.substring(0, 4);

        // Converti le lettere: A=10, B=11, ..., Z=35
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isLetter(c)) {
                numeric.append(c - 'A' + 10);
            } else {
                numeric.append(c);
            }
        }

        // Calcola il resto modulo 97 a blocchi di 9 cifre (evita BigInteger).
        // Il resto precedente puo' avere al massimo 2 cifre (0-96), il blocco successivo
        // ne ha al massimo 9, quindi il numero da parsare e' al massimo 11 cifre:
        // Long gestisce fino a 19 cifre, Integer non basta (max 10 cifre).
        long remainder = 0;
        int i = 0;
        String numericStr = numeric.toString();
        while (i < numericStr.length()) {
            // Prendi fino a 9 cifre dalla posizione corrente, anteponi il resto corrente
            int end = Math.min(i + 9, numericStr.length());
            String block = (i == 0 ? "" : String.valueOf(remainder)) + numericStr.substring(i, end);
            remainder = Long.parseLong(block) % 97;
            i = end;
        }

        if (remainder != 1L) {
            return "Questo IBAN non supera il controllo di validita': probabilmente c'e' una "
                    + "cifra sbagliata. Rileggilo con attenzione e riprova.";
        }

        return null; // Valido
    }

    /**
     * Formatta l'IBAN a gruppi di 4 caratteri separati da spazio, per facilitare la lettura.
     * Esempio: IT60 X054 2811 1010 0000 0123 456
     */
    private String formatIban(String iban) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < iban.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append(' ');
            }
            sb.append(iban.charAt(i));
        }
        return sb.toString();
    }
}
