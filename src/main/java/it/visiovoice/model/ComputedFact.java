package it.visiovoice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Objects;

/**
 * Un valore ottenuto da altri valori: una somma, un prodotto, una proiezione.
 *
 * <p>Sta in un tipo separato dai {@link SourceFact} per una ragione sola, ed e' la ragione per
 * cui questo tipo esiste: un valore calcolato e' {@link Provenance#AI_INFERRED} <b>per
 * costruzione</b>, non perche' il gate non e' riuscito a ritrovarlo. Tenerlo in un array
 * distinto rende quella differenza strutturale invece di dedotta, e toglie al gate una
 * decisione che non e' in grado di prendere: davanti a un numero che non trova in nessuna
 * fonte, il gate non puo' distinguere "calcolato correttamente" da "inventato".
 *
 * <p>Conseguenza operativa: i valori calcolati <b>non entrano</b> nel corpus di ancoraggio
 * (vedi {@link Scenario#sourceCorpus()}). Se entrassero, un importo moltiplicato per due
 * anchorerebbe se stesso e il gate direbbe "verificato" di un numero che nessuna fonte
 * contiene.
 *
 * @param id          riferimento citabile dai {@code sourceRefs} dei segmenti
 * @param text        il valore calcolato, nella forma in cui verrebbe pronunciato
 * @param formula     come si ottiene, in forma leggibile da una persona
 * @param derivedFrom gli id dei {@link SourceFact} da cui deriva: e' cio' che rende il calcolo
 *                    controllabile a mano da chi rivede
 * @param ref         nota di contesto: chi ha deciso che questo calcolo si fa cosi'
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ComputedFact(
        String id,
        String text,
        String formula,
        List<String> derivedFrom,
        String ref) {

    public ComputedFact {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        derivedFrom = List.copyOf(derivedFrom == null ? List.of() : derivedFrom);
    }

    /**
     * Sempre {@link Provenance#AI_INFERRED}. Non e' un campo e non si puo' sovrascrivere: se
     * fosse un campo, prima o poi qualcuno lo metterebbe a {@code AI_REPHRASED} per far tacere
     * l'avvertenza, e l'avvertenza e' il prodotto.
     */
    public Provenance provenance() {
        return Provenance.AI_INFERRED;
    }
}
