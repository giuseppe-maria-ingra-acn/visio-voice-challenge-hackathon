package it.visiovoice.validation;

import it.visiovoice.agents.ValidationPort;
import it.visiovoice.model.FormField;
import it.visiovoice.model.ValidationResult;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Il validatore composito: il bean {@link ValidationPort} principale nel contesto.
 *
 * <p>Delega a validatori specializzati ({@link IbanValidator}, {@link CodiceFiscaleValidator},
 * {@link DateValidator}) e gestisce il caso in cui nessun validatore conosce il campo.
 * E' annotato {@code @Primary} perche' i validatori specializzati sono anch'essi bean
 * Spring (per poter essere iniettati qui), ma il punto di ingresso unico e' questo.
 */
@Primary
@Service
public class ValidationService implements ValidationPort {

    private final List<ValidationPort> validators;

    public ValidationService(IbanValidator ibanValidator,
                             CodiceFiscaleValidator codiceFiscaleValidator,
                             DateValidator dateValidator) {
        this.validators = List.of(ibanValidator, codiceFiscaleValidator, dateValidator);
    }

    @Override
    public boolean supports(FormField field) {
        return true; // Il servizio composito accetta qualsiasi campo
    }

    @Override
    public ValidationResult validate(FormField field, String userInput) {
        if (field == null) {
            return ValidationResult.invalid(null,
                    "Non riesco a identificare il campo da controllare. Riprova.");
        }

        // Delega al validatore specializzato se ne esiste uno
        for (ValidationPort validator : validators) {
            if (validator.supports(field)) {
                return validator.validate(field, userInput);
            }
        }

        // Nessun validatore specializzato: controllo di base (campo obbligatorio vuoto)
        if (field.required() && (userInput == null || userInput.isBlank())) {
            return ValidationResult.invalid(field.id(),
                    "Questo campo e' obbligatorio, altrimenti la domanda non puo' essere inviata. "
                    + "Inserisci il valore richiesto e riprova.");
        }

        // Campo opzionale o compilato senza un validatore specifico: accettato
        String msg = (userInput == null || userInput.isBlank())
                ? "Il campo e' vuoto."
                : "Valore registrato: " + userInput.trim() + ".";
        return ValidationResult.valid(field.id(), msg, userInput == null ? null : userInput.trim());
    }
}
