package it.visiovoice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Un passo della procedura come lo dichiara lo scenario.
 *
 * @param step         numero del passo. Il passo 0 e' la pagina informativa: non si compila
 *                     nulla, ma e' dove sta la barriera peggiore
 * @param requiredData cosa bisogna avere <b>prima</b> di cominciare. Dirlo all'inizio e non a
 *                     meta' e' la differenza fra una procedura completabile e una da rifare
 * @param completionCriterion come si capisce che il passo e' finito, se lo scenario lo
 *                     dichiara. Opzionale: quando manca, chi costruisce il piano lo deduce
 *                     dai campi obbligatori
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScenarioStep(
        int step,
        String title,
        List<String> requiredData,
        List<ScenarioField> fields,
        String note,
        ScenarioOutput output,
        String completionCriterion) {

    public ScenarioStep {
        requiredData = List.copyOf(requiredData == null ? List.of() : requiredData);
        fields = List.copyOf(fields == null ? List.of() : fields);
    }

    public Optional<ScenarioField> field(String fieldId) {
        Objects.requireNonNull(fieldId, "fieldId");
        return fields.stream().filter(f -> f.id().equals(fieldId)).findFirst();
    }

    /** Gli id di tutte le barriere che colpiscono i campi di questo passo. */
    public List<String> barrierIds() {
        return fields.stream().flatMap(f -> f.barrierIds().stream()).distinct().toList();
    }
}
