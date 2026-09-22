package it.visiovoice.validation;

import it.visiovoice.agents.ValidationPort;
import it.visiovoice.model.FieldKind;
import it.visiovoice.model.FormField;
import it.visiovoice.model.ValidationResult;
import org.springframework.stereotype.Service;

/**
 * Validatore Codice Fiscale italiano secondo DM 23/12/1976.
 *
 * <p>Algoritmo del carattere di controllo: tabelle di conversione per posizioni pari e dispari
 * (1-based), somma modulo 26, mappatura su lettera. Gestisce l'omocodia: le cifre nelle
 * posizioni 7, 9, 11, 13, 14, 16 (1-based, contando solo i caratteri alfanumerici dopo i
 * primi 6) possono essere sostituite con lettere speciali (0=L, 1=M, 2=N, 3=P, 4=Q, 5=R,
 * 6=S, 7=T, 8=U, 9=V).
 *
 * <p>Il messaggio di errore e' una frase pronunciabile: dice cosa e' sbagliato e cosa fare.
 */
@Service
public class CodiceFiscaleValidator implements ValidationPort {

    /** Tabella valori per carattere in posizione dispari (1-based: 1,3,5,...). */
    private static final int[] ODD_VALUES = {
        /* A */ 1, /* B */ 0, /* C */ 5, /* D */ 7, /* E */ 9, /* F */ 13,
        /* G */ 15, /* H */ 17, /* I */ 19, /* J */ 21, /* K */ 2, /* L */ 4,
        /* M */ 18, /* N */ 20, /* O */ 11, /* P */ 3, /* Q */ 6, /* R */ 8,
        /* S */ 12, /* T */ 14, /* U */ 16, /* V */ 10, /* W */ 22, /* X */ 25,
        /* Y */ 24, /* Z */ 23
    };

    /** Tabella valori per carattere in posizione pari (1-based: 2,4,6,...). */
    private static final int[] EVEN_VALUES = {
        /* A=0 */ 0, /* B=1 */ 1, /* C=2 */ 2, /* D=3 */ 3, /* E=4 */ 4, /* F=5 */ 5,
        /* G=6 */ 6, /* H=7 */ 7, /* I=8 */ 8, /* J=9 */ 9, /* K=10 */ 10, /* L=11 */ 11,
        /* M=12 */ 12, /* N=13 */ 13, /* O=14 */ 14, /* P=15 */ 15, /* Q=16 */ 16,
        /* R=17 */ 17, /* S=18 */ 18, /* T=19 */ 19, /* U=20 */ 20, /* V=21 */ 21,
        /* W=22 */ 22, /* X=23 */ 23, /* Y=24 */ 24, /* Z=25 */ 25
    };

    /**
     * Tabella di omocodia: le cifre 0-9 possono essere sostituite con queste lettere nelle
     * posizioni soggette a omocodia.
     */
    private static final char[] OMOCODIA_CHARS = {
        'L', 'M', 'N', 'P', 'Q', 'R', 'S', 'T', 'U', 'V'
    };

    /** Mappatura inversa: carattere di omocodia -> valore numerico della cifra originale. */
    private static int omocodiaValue(char c) {
        for (int i = 0; i < OMOCODIA_CHARS.length; i++) {
            if (OMOCODIA_CHARS[i] == c) return i;
        }
        return -1; // Non e' un carattere di omocodia
    }

    @Override
    public boolean supports(FormField field) {
        return field != null && field.kind() == FieldKind.FISCAL_CODE;
    }

    @Override
    public ValidationResult validate(FormField field, String userInput) {
        String fieldId = field != null ? field.id() : null;

        if (userInput == null || userInput.isBlank()) {
            if (field != null && field.required()) {
                return ValidationResult.invalid(fieldId,
                        "Questo campo e' obbligatorio: il codice fiscale serve per identificarti "
                        + "nell'Anagrafe Tributaria. Trovalo sul tuo documento d'identita' o sulla "
                        + "tessera sanitaria.");
            }
            return ValidationResult.valid(fieldId,
                    "Il campo codice fiscale e' vuoto.", null);
        }

        String normalized = userInput.replaceAll("\\s+", "").toUpperCase();

        if (normalized.length() != 16) {
            int actual = normalized.length();
            return ValidationResult.invalid(fieldId,
                    "Il codice fiscale deve avere 16 caratteri, tu ne hai scritti " + actual
                    + ". Controlla di averlo copiato per intero dalla tessera sanitaria o dal "
                    + "documento d'identita'.");
        }

        // Controlla che siano solo caratteri alfanumerici maiuscoli
        if (!normalized.matches("[A-Z0-9]+")) {
            return ValidationResult.invalid(fieldId,
                    "Il codice fiscale contiene caratteri non validi: deve essere composto solo "
                    + "da lettere maiuscole e cifre. Controlla se hai inserito simboli o spazi.");
        }

        // Calcola il carattere di controllo atteso
        char expectedControl = computeControlChar(normalized.substring(0, 15));
        char actualControl = normalized.charAt(15);

        if (actualControl != expectedControl) {
            return ValidationResult.invalid(fieldId,
                    "Il codice fiscale non supera il controllo di validita': l'ultimo carattere "
                    + "dovrebbe essere " + expectedControl + " ma hai scritto " + actualControl
                    + ". Ricontrollalo dalla tessera sanitaria o dal documento d'identita'.");
        }

        return ValidationResult.valid(fieldId,
                "Il codice fiscale e' corretto.", normalized);
    }

    /**
     * Calcola il carattere di controllo a partire dai primi 15 caratteri del codice fiscale,
     * gestendo l'omocodia.
     *
     * <p>Le posizioni soggette a omocodia sono quelle normalmente numeriche: anno (pos 7-8,
     * indici 6-7), giorno (pos 10-11, indici 9-10), codice comune ultime tre cifre (pos 13-15,
     * indici 12-14). In queste posizioni, se e' presente una lettera del gruppo L,M,N,P,Q,R,S,
     * T,U,V (sostituzioni di omocodia per le cifre 0-9), viene prima convertita nella cifra
     * originale prima di consultare la tabella. Le altre posizioni (cognome, nome, codice mese,
     * prima lettera del comune) accettano solo lettere che vengono consultate direttamente.
     */
    private char computeControlChar(String first15) {
        // Indici (0-based) delle posizioni soggette a omocodia: 6,7 (anno), 9,10 (giorno),
        // 12,13,14 (ultime tre cifre del codice comune).
        // La posizione 8 (codice mese, sempre una lettera A-T) e la 11 (prima lettera comune)
        // NON sono soggette a omocodia e non si risolve.
        boolean[] omocodiaPos = {
            false, false, false, false, false, false, // indici 0-5: cognome+nome (lettere)
            true, true,                                // indici 6-7: anno di nascita (cifre)
            false,                                     // indice 8: mese (lettera A-T, mai omocodia)
            true, true,                                // indici 9-10: giorno (cifre)
            false,                                     // indice 11: prima lettera comune (mai omocodia)
            true, true, true                           // indici 12-14: ultime tre cifre comune
        };

        int sum = 0;
        for (int i = 0; i < 15; i++) {
            char c = first15.charAt(i);
            // Converti eventuale carattere di omocodia solo nelle posizioni numeriche
            if (omocodiaPos[i]) {
                c = resolveOmocodia(c);
            }
            // Posizione 1-based: i+1. Dispari = 1,3,5,... -> ODD. Pari = 2,4,6,... -> EVEN.
            if ((i + 1) % 2 == 1) {
                // Posizione dispari: secondo DM 23/12/1976, le cifre 0-9 usano gli stessi
                // indici delle lettere A-J nella tabella dispari (0=A=1, 1=B=0, ecc.).
                if (Character.isDigit(c)) {
                    sum += ODD_VALUES[c - '0'];
                } else {
                    sum += ODD_VALUES[c - 'A'];
                }
            } else {
                // Posizione pari
                if (Character.isDigit(c)) {
                    sum += EVEN_VALUES[c - '0'];
                } else {
                    sum += EVEN_VALUES[c - 'A'];
                }
            }
        }
        return (char) ('A' + (sum % 26));
    }

    /**
     * Se il carattere e' un carattere di omocodia, lo converte nella cifra originale.
     * Altrimenti lo restituisce immutato.
     */
    private char resolveOmocodia(char c) {
        int omoValue = omocodiaValue(c);
        if (omoValue >= 0) {
            return (char) ('0' + omoValue);
        }
        return c;
    }
}
