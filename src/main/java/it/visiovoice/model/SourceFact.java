package it.visiovoice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;

/**
 * Un dato preso da una fonte citabile, con la citazione attaccata.
 *
 * <p>I {@code sourceFacts} di uno scenario sono il <b>corpus</b> del gate di fidelity: un
 * numero pronunciato da VisioVoice deve ritrovarsi qui dentro, o il segmento che lo contiene
 * viene retrocesso. Per questo il testo va tenuto nella forma in cui sta nella fonte, non
 * riformulato: e' materiale di confronto, non prosa.
 *
 * @param id   riferimento citabile dai {@code sourceRefs} dei segmenti
 * @param text il dato come sta nella fonte
 * @param ref  la citazione: documento, URL, data di consultazione
 * @param provenance dichiarata nello scenario dall'autore, non indovinata dal gate. Se lo
 *                   scenario non la dichiara si assume {@link Provenance#SOURCE_VERBATIM},
 *                   che e' la definizione stessa di "fatto citato da una fonte"; dichiararla
 *                   comunque e' preferibile, perche' un fatto normalizzato a mano da una
 *                   tabella e' piu' onestamente {@link Provenance#AI_REPHRASED}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SourceFact(
        String id,
        String text,
        String ref,
        Provenance provenance) {

    public SourceFact {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        provenance = provenance == null ? Provenance.SOURCE_VERBATIM : provenance;
    }

    public static SourceFact of(String id, String text, String ref) {
        return new SourceFact(id, text, ref, Provenance.SOURCE_VERBATIM);
    }
}
