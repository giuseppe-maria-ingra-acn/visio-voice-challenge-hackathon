package it.visiovoice.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * La procedura come sequenza di passi, con l'obiettivo in cima.
 *
 * <p>Il piano non contiene i valori inseriti. E' deliberato: i dati stanno nel DOM della
 * pagina, che e' la fonte di verita'. Se il piano ne tenesse una copia, VisioVoice diventerebbe
 * un intermediario che puo' sbagliare a ritrasmettere cio' che l'utente ha scritto, e nessuno
 * se ne accorgerebbe.
 *
 * @param goal cosa l'utente vuole ottenere, detto nelle sue parole e non nelle nostre
 */
public record ProcedurePlan(
        String scenarioId,
        String goal,
        List<ProcedureStep> steps) {

    public ProcedurePlan {
        Objects.requireNonNull(scenarioId, "scenarioId");
        steps = List.copyOf(steps == null ? List.of() : steps);
    }

    public Optional<ProcedureStep> step(int index) {
        return steps.stream().filter(s -> s.index() == index).findFirst();
    }

    public int totalSteps() {
        return steps.size();
    }

    /** Tutti i campi di tutti i passi, nell'ordine della procedura. */
    public List<FormField> allFields() {
        return steps.stream().flatMap(s -> s.fields().stream()).toList();
    }

    public Optional<FormField> field(String fieldId) {
        return allFields().stream().filter(f -> f.id().equals(fieldId)).findFirst();
    }
}
