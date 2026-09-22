package it.visiovoice.api;

import it.visiovoice.api.dto.ErrorResponse;
import it.visiovoice.api.dto.ProvenanceResponse;
import it.visiovoice.model.ProvenanceGate;
import it.visiovoice.model.ScriptSegment;
import it.visiovoice.orchestrator.ScriptStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Da dove viene una frase che l'utente ha sentito.
 *
 * <p>Non e' un endpoint di diagnostica. E' il modo in cui chi non puo' guardare lo schermo
 * verifica quanto fidarsi di cio' che ha appena ascoltato: l'unica forma di controllo che gli
 * resta. Per questo la risposta contiene una spiegazione gia' pronunciabile e non solo il nome
 * di un livello.
 */
@RestController
@RequestMapping("/api/provenance")
public class ProvenanceController {

    private final ScriptStore scripts;

    public ProvenanceController(ScriptStore scripts) {
        this.scripts = scripts;
    }

    @GetMapping("/{segmentId}")
    public ResponseEntity<Object> describe(@PathVariable String segmentId) {
        return scripts.segment(segmentId)
                .<ResponseEntity<Object>>map(segment -> ResponseEntity.ok(toResponse(segment)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                        "frase-sconosciuta",
                        "Non ritrovo la frase a cui ti riferisci. Posso rileggerti la schermata "
                        + "dall'inizio.")));
    }

    private static ProvenanceResponse toResponse(ScriptSegment segment) {
        return new ProvenanceResponse(
                segment.id(),
                segment.text(),
                segment.provenance(),
                segment.provenance().italianLabel(),
                segment.needsHumanReview(),
                segment.sourceRefs(),
                explain(segment));
    }

    /**
     * La spiegazione da leggere a voce alta. Dichiara anche il limite del controllo quando il
     * segmento e' riformulato: dire "verificato" senza aggiungere su cosa sarebbe la stessa
     * pretesa eccessiva che il gate serve a prevenire.
     */
    private static String explain(ScriptSegment segment) {
        StringBuilder sb = new StringBuilder();
        sb.append("Questa frase e' ").append(segment.provenance().italianLabel()).append('.');
        if (!segment.sourceRefs().isEmpty()) {
            sb.append(" Si appoggia a ").append(segment.sourceRefs().size())
              .append(segment.sourceRefs().size() == 1 ? " riferimento" : " riferimenti")
              .append(": ").append(String.join(", ", segment.sourceRefs())).append('.');
        }
        switch (segment.provenance()) {
            case SOURCE_VERBATIM -> sb.append(" L'ho letta cosi' com'era scritta nella pagina.");
            case AI_REPHRASED -> sb.append(' ').append(ProvenanceGate.SEMANTIC_LIMIT_NOTE);
            case AI_INFERRED -> sb.append(" Non l'ho trovata scritta da nessuna parte: l'ho "
                    + "ricavata io, e conviene farla controllare.");
            case HUMAN_REVIEWED -> sb.append(" Una persona l'ha letta e approvata.");
        }
        return sb.toString();
    }
}
