package it.visiovoice.narration;

import it.visiovoice.model.FidelityVerdict;
import it.visiovoice.model.ProvenanceGate;
import it.visiovoice.model.SourceFact;
import it.visiovoice.model.SpokenScript;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Applica il gate di fidelity a uno script di narrazione.
 *
 * <p>Non reimplementa la regola: usa {@link ProvenanceGate#verify} che sta in {@code model/} e
 * che e' la sola implementazione normativa. Esistere come componente separato serve a due cose:
 * rende il gate iniettabile negli strati che ne hanno bisogno, e rende il punto di applicazione
 * trovabile con un grep.
 *
 * <p>Nessuna chiamata a modelli generativi in questa classe: il gate e' deterministico per
 * costruzione. Un modello che verifica un modello condivide i suoi punti ciechi; un
 * {@code String.contains} su testo normalizzato gira in microsecondi ed e' riproducibile.
 */
@Component
public class FidelityAgent {

    /**
     * Verifica lo script contro i fatti noti e un corpus aggiuntivo (tipicamente
     * {@link it.visiovoice.model.ScreenModel#textCorpus()}).
     *
     * @return il verdetto con lo script gia' retrocesso e il rapporto di fidelity
     */
    public FidelityVerdict verify(SpokenScript script, List<SourceFact> facts, String extraCorpus) {
        return ProvenanceGate.verify(script, facts, extraCorpus);
    }

    /**
     * Come sopra, senza corpus aggiuntivo: per usi che non hanno accesso al testo della pagina.
     */
    public FidelityVerdict verify(SpokenScript script, List<SourceFact> facts) {
        return ProvenanceGate.verify(script, facts);
    }
}
