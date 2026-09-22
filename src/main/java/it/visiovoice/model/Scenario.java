package it.visiovoice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lo scenario: il servizio, le sue barriere, i fatti citati e la procedura, come dati.
 *
 * <p>Sta in un file JSON e non nel codice per una ragione che il gate rende concreta: un
 * importo scritto in un {@code .java} non ha una fonte a cui riancorarlo, quindi verrebbe
 * pronunciato senza che nulla lo verifichi. Qui ogni dato porta la propria citazione.
 *
 * @param sourceFacts   dati presi da fonti citabili. Sono il corpus del gate
 * @param computedFacts valori calcolati a partire dai precedenti. Tenuti separati perche' sono
 *                      dedotti per costruzione: vedi {@link ComputedFact}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Scenario(
        String id,
        ServiceInfo service,
        String persona,
        String goal,
        List<Barrier> barriers,
        List<SourceFact> sourceFacts,
        List<ComputedFact> computedFacts,
        List<ScenarioStep> procedure) {

    public Scenario {
        Objects.requireNonNull(id, "id");
        barriers = List.copyOf(barriers == null ? List.of() : barriers);
        sourceFacts = List.copyOf(sourceFacts == null ? List.of() : sourceFacts);
        computedFacts = List.copyOf(computedFacts == null ? List.of() : computedFacts);
        procedure = List.copyOf(procedure == null ? List.of() : procedure);
    }

    public Optional<Barrier> barrier(String barrierId) {
        return barriers.stream().filter(b -> b.id().equals(barrierId)).findFirst();
    }

    public Optional<SourceFact> sourceFact(String factId) {
        return sourceFacts.stream().filter(f -> f.id().equals(factId)).findFirst();
    }

    public Optional<ComputedFact> computedFact(String factId) {
        return computedFacts.stream().filter(f -> f.id().equals(factId)).findFirst();
    }

    public Optional<ScenarioStep> step(int index) {
        return procedure.stream().filter(s -> s.step() == index).findFirst();
    }

    /** Le barriere di un certo tipo: serve alla demo per mostrare che sono tutte coperte. */
    public List<Barrier> barriersOfType(BarrierType type) {
        return barriers.stream().filter(b -> b.type() == type).toList();
    }

    /**
     * Il corpus su cui {@link ProvenanceGate} riancora numeri e nomi propri.
     *
     * <p>Contiene i {@link SourceFact} e <b>non</b> i {@link ComputedFact}. La ragione e' il
     * cuore della distinzione fra i due tipi: se un valore calcolato stesse nel corpus,
     * anchorerebbe se stesso, e il gate direbbe "verificato sulla fonte" di un numero che
     * nessuna fonte contiene. Un valore calcolato resta dedotto e va annunciato come tale.
     */
    public String sourceCorpus() {
        StringBuilder sb = new StringBuilder();
        if (service != null) {
            sb.append(service.name()).append('\n').append(service.authority()).append('\n');
        }
        sourceFacts.forEach(f -> sb.append(f.text()).append('\n').append(f.ref()).append('\n'));
        procedure.forEach(step -> {
            sb.append(step.title()).append('\n');
            step.requiredData().forEach(d -> sb.append(d).append('\n'));
            step.fields().forEach(f -> sb.append(f.label()).append('\n')
                    .append(f.format()).append('\n').append(f.example()).append('\n'));
        });
        return sb.toString();
    }
}
