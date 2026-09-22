package it.visiovoice.api.dto;

import it.visiovoice.model.Provenance;
import java.util.List;

/**
 * La risposta alla domanda "da dove viene quello che hai appena detto?".
 *
 * <p>Alimenta il comando dedicato dell'interfaccia. Non e' un endpoint di diagnostica: e' il
 * modo in cui chi non puo' controllare lo schermo decide quanto fidarsi di cio' che ha sentito.
 *
 * @param spokenExplanation la spiegazione pronunciabile, gia' composta: livello, cosa
 *                          significa, e su quali fonti si appoggia
 */
public record ProvenanceResponse(
        String segmentId,
        String text,
        Provenance provenance,
        String italianLabel,
        boolean needsHumanReview,
        List<String> sourceRefs,
        String spokenExplanation) {
}
