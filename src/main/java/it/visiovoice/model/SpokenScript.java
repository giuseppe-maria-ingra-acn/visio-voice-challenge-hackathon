package it.visiovoice.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Il racconto di una schermata: segmenti in ordine di lettura, a un dato livello di dettaglio.
 *
 * <p>Ordinato e non un insieme: l'ordine e' il prodotto. Chi non vede lo schermo non puo'
 * scegliere da dove cominciare a guardare, quindi la sequenza che gli proponiamo e' l'unica
 * che avra'.
 *
 * @param id        identificativo dello script, la chiave di {@code GET /api/narrate/id}
 * @param screenId  la schermata raccontata
 * @param level     il livello di dettaglio con cui e' stato generato
 * @param segments  i segmenti, gia' nell'ordine in cui vanno pronunciati
 */
public record SpokenScript(
        String id,
        String screenId,
        DetailLevel level,
        List<ScriptSegment> segments) {

    public SpokenScript {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(level, "level");
        segments = List.copyOf(segments == null ? List.of() : segments);
    }

    public static SpokenScript of(String id, String screenId, DetailLevel level, List<ScriptSegment> segments) {
        return new SpokenScript(id, screenId, level, segments);
    }

    /** Copia con altri segmenti: e' cosi' che il gate restituisce uno script con retrocessioni. */
    public SpokenScript withSegments(List<ScriptSegment> newSegments) {
        return new SpokenScript(id, screenId, level, newSegments);
    }

    /** Il segmento con questo id, se c'e'. Serve al comando che chiede la provenienza. */
    public Optional<ScriptSegment> segment(String segmentId) {
        return segments.stream().filter(s -> s.id().equals(segmentId)).findFirst();
    }

    /** I segmenti di un certo ruolo, nell'ordine originale. */
    public List<ScriptSegment> byRole(SegmentRole role) {
        return segments.stream().filter(s -> s.role() == role).toList();
    }

    /**
     * La provenance dell'intero racconto: quella del suo anello piu' debole. Uno script che
     * contiene una sola frase dedotta non e' uno script verificato.
     */
    public Provenance weakestProvenance() {
        Provenance worst = Provenance.HUMAN_REVIEWED;
        for (ScriptSegment segment : segments) {
            worst = Provenance.weakest(worst, segment.provenance());
        }
        return worst;
    }

    /** Se true, almeno un segmento va annunciato come dedotto. */
    public boolean needsHumanReview() {
        return segments.stream().anyMatch(ScriptSegment::needsHumanReview);
    }

    /** Tutto il testo di seguito, per i test e per i log. Non e' cio' che si pronuncia. */
    public String plainText() {
        return segments.stream().map(ScriptSegment::text).collect(Collectors.joining(" "));
    }
}
