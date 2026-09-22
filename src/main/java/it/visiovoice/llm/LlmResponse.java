package it.visiovoice.llm;

import it.visiovoice.model.Provenance;
import java.util.List;
import java.util.Objects;

/**
 * Cio' che il modello ha risposto.
 *
 * @param fromFixture true se la risposta viene da una fixture su disco. Compare in
 *                    {@code /api/health} e nella demo: dichiarare che il racconto arriva da una
 *                    risposta registrata e non da un modello dal vivo e' la stessa onesta' che
 *                    chiediamo al gate
 * @param claimedProvenance la provenance che il modello <b>dichiara</b> per il proprio testo.
 *                    E' una pretesa, non un verdetto: chi decide e'
 *                    {@link it.visiovoice.model.ProvenanceGate}, e puo' smentirla. Il nome dice
 *                    "claimed" proprio per impedire che qualcuno la copi dentro un
 *                    {@code ScriptSegment} come se fosse accertata
 * @param sourceRefs  i riferimenti che il modello afferma di avere usato, da passare al gate
 */
public record LlmResponse(
        String text,
        String model,
        boolean fromFixture,
        Provenance claimedProvenance,
        List<String> sourceRefs,
        long latencyMs) {

    public LlmResponse {
        Objects.requireNonNull(text, "text");
        claimedProvenance = claimedProvenance == null ? Provenance.AI_INFERRED : claimedProvenance;
        sourceRefs = List.copyOf(sourceRefs == null ? List.of() : sourceRefs);
    }

    public static LlmResponse fixture(String text, String model, List<String> sourceRefs, long latencyMs) {
        return new LlmResponse(text, model, true, Provenance.AI_REPHRASED, sourceRefs, latencyMs);
    }

    public boolean isEmpty() {
        return text.isBlank();
    }
}
