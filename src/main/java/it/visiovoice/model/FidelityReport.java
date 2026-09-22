package it.visiovoice.model;

import java.util.List;
import java.util.Objects;

/**
 * Cosa ha trovato il gate: quali segmenti ha retrocesso, quali token non si ancorano, cosa
 * serve che una persona rilegga.
 *
 * <p>Da qui si genera {@code docs/AI-CONTRIBUTION.md}. La mappa di dove ha lavorato l'AI non
 * si scrive a mano la sera prima: si legge da questo oggetto, che e' il verbale di un
 * controllo meccanico.
 *
 * @param demotedSegmentIds i segmenti che chiedevano {@link Provenance#AI_REPHRASED} e si sono
 *                          ritrovati {@link Provenance#AI_INFERRED}
 * @param unanchored        i token che non si ritrovano nella fonte
 * @param reviewNeeded      cosa chiediamo a una persona di verificare, in italiano. Comprende
 *                          cio' che questo controllo <b>non</b> sa fare: una parafrasi con i
 *                          numeri giusti e il senso rovesciato passa il gate, e va detto
 * @param segmentsChecked   quanti segmenti sono stati esaminati: un rapporto che dichiara zero
 *                          problemi su zero segmenti non e' un rapporto pulito
 */
public record FidelityReport(
        String scriptId,
        int segmentsChecked,
        List<String> demotedSegmentIds,
        List<UnanchoredToken> unanchored,
        List<String> reviewNeeded) {

    public FidelityReport {
        demotedSegmentIds = List.copyOf(demotedSegmentIds == null ? List.of() : demotedSegmentIds);
        unanchored = List.copyOf(unanchored == null ? List.of() : unanchored);
        reviewNeeded = List.copyOf(reviewNeeded == null ? List.of() : reviewNeeded);
    }

    public static FidelityReport clean(String scriptId, int segmentsChecked) {
        return new FidelityReport(scriptId, segmentsChecked, List.of(), List.of(), List.of());
    }

    /** Nessuna retrocessione e nessun token orfano. Non dice nulla sulla correttezza del senso. */
    public boolean isClean() {
        return demotedSegmentIds.isEmpty() && unanchored.isEmpty();
    }

    public boolean hasFindings() {
        return !isClean();
    }

    /** Riepilogo pronunciabile, per l'utente che chiede quanto fidarsi di cio' che ha sentito. */
    public String spokenSummary() {
        Objects.requireNonNull(scriptId, "scriptId");
        if (segmentsChecked == 0) {
            return "Non ho ancora controllato nulla di questa schermata.";
        }
        if (isClean()) {
            return "Ho controllato " + segmentsChecked
                    + " frasi: ogni numero e ogni nome che ho detto si ritrova nella fonte.";
        }
        return "Ho controllato " + segmentsChecked + " frasi. In " + demotedSegmentIds.size()
                + " non ho ritrovato tutto nella fonte: quelle le ho annunciate come dedotte.";
    }
}
