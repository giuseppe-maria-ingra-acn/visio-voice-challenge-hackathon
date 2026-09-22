package it.visiovoice.model;

import java.util.List;
import java.util.Objects;

/**
 * Un passo del piano: cosa serve avere, cosa si compila, come si capisce che e' finito.
 *
 * <p>Distinto da {@link ScenarioStep} perche' quello e' documentazione e questo e' il piano
 * eseguibile: i suoi campi sono {@link FormField}, cioe' portano la provenance dell'etichetta,
 * e il criterio di completamento e' sempre presente.
 *
 * @param completionCriterion come si capisce che il passo e' concluso, in italiano
 *                            pronunciabile. Obbligatorio: senza di questo l'utente non sa
 *                            quando puo' andare avanti, e "vai avanti quando hai finito" a chi
 *                            non vede la pagina non dice nulla
 * @param provenance          da dove viene il testo di questo passo: titolo e criterio
 */
public record ProcedureStep(
        int index,
        String title,
        List<String> requiredData,
        List<FormField> fields,
        String completionCriterion,
        Provenance provenance) {

    public ProcedureStep {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(completionCriterion, "completionCriterion");
        Objects.requireNonNull(provenance, "provenance");
        requiredData = List.copyOf(requiredData == null ? List.of() : requiredData);
        fields = List.copyOf(fields == null ? List.of() : fields);
    }

    /** I campi senza i quali il passo non si chiude. */
    public List<FormField> requiredFields() {
        return fields.stream().filter(FormField::required).toList();
    }

    /**
     * Vero se i valori letti dal DOM coprono tutti i campi obbligatori di questo passo. I
     * valori arrivano da fuori: il piano non li conserva, la pagina e' la fonte di verita'.
     */
    public boolean isSatisfiedBy(java.util.Map<String, String> currentValues) {
        java.util.Map<String, String> values = currentValues == null ? java.util.Map.of() : currentValues;
        return requiredFields().stream()
                .allMatch(f -> {
                    String value = values.get(f.id());
                    return value != null && !value.isBlank();
                });
    }
}
