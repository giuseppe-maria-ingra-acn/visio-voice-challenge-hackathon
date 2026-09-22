package it.visiovoice.agents;

import it.visiovoice.model.FormField;
import it.visiovoice.model.ValidationResult;

/**
 * I controlli sui valori: codice fiscale, IBAN, date, importi.
 *
 * <p>Algoritmi normati con vettori di test noti, nessun modello generativo. Non lanciano
 * eccezioni: restituiscono un esito con una frase pronunciabile, perche' un'eccezione che
 * arriva all'interfaccia diventa testo che una sintesi vocale legge all'utente.
 */
public interface ValidationPort {

    /**
     * Giudica un valore.
     *
     * @param field     il campo, che porta con se' il tipo di dato atteso
     * @param userInput il valore letto dal campo. Puo' essere vuoto o nullo: "non l'hai ancora
     *                  compilato" e' un esito legittimo e va detto, non e' un errore di
     *                  programmazione
     */
    ValidationResult validate(FormField field, String userInput);

    /** True se questo validatore sa giudicare questo campo. */
    boolean supports(FormField field);
}
