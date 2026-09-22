package it.visiovoice.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cio' che c'e' su una schermata, dopo che la pagina e' stata letta e prima che venga
 * raccontata.
 *
 * <p>E' il confine fra la parte del sistema che non puo' allucinare e quella che potrebbe:
 * lo costruisce {@code perception} con jsoup, in modo deterministico, e da qui in avanti
 * nessuno inventa un campo che non c'era. Tutti i tipi che ci stanno dentro descrivono
 * <b>osservazioni</b>; le interpretazioni stanno in {@link SpokenScript}.
 *
 * @param stepIndex passo corrente della procedura, 0 se la pagina non fa parte di una
 *                  procedura a passi o se non si riesce a determinarlo. Sta qui e non solo nel
 *                  piano perche' su questa pagina lo stato e' scritto in un PNG, e sapere che
 *                  non si e' riusciti a leggerlo e' un'informazione
 * @param stepCount quanti passi in tutto, 0 se sconosciuto
 */
public record ScreenModel(
        String screenId,
        String url,
        String title,
        int stepIndex,
        int stepCount,
        List<ScreenRegion> regions,
        List<FormField> fields,
        List<VisualAsset> visuals,
        List<Barrier> barriers) {

    public ScreenModel {
        Objects.requireNonNull(screenId, "screenId");
        regions = List.copyOf(regions == null ? List.of() : regions);
        fields = List.copyOf(fields == null ? List.of() : fields);
        visuals = List.copyOf(visuals == null ? List.of() : visuals);
        barriers = List.copyOf(barriers == null ? List.of() : barriers);
    }

    public Optional<FormField> field(String fieldId) {
        return fields.stream().filter(f -> f.id().equals(fieldId)).findFirst();
    }

    public Optional<Barrier> barrier(String barrierId) {
        return barriers.stream().filter(b -> b.id().equals(barrierId)).findFirst();
    }

    /** Le barriere che colpiscono un campo preciso, risolte dagli id che il campo dichiara. */
    public List<Barrier> barriersFor(String fieldId) {
        return field(fieldId)
                .map(f -> f.barrierIds().stream().flatMap(id -> barrier(id).stream()).toList())
                .orElse(List.of());
    }

    /** True se questa pagina dichiara di stare dentro una procedura a passi. */
    public boolean hasSteps() {
        return stepCount > 0;
    }

    /**
     * Tutto il testo osservato sulla pagina, nell'ordine. E' il corpus di ancoraggio lato DOM,
     * complementare a quello dei fatti dello scenario.
     */
    public String textCorpus() {
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append(title).append('\n');
        }
        regions.forEach(r -> {
            if (r.heading() != null) {
                sb.append(r.heading()).append('\n');
            }
            sb.append(r.text()).append('\n');
        });
        fields.forEach(f -> {
            if (f.label() != null) {
                sb.append(f.label()).append('\n');
            }
        });
        visuals.forEach(v -> sb.append(v.altText()).append('\n').append(v.nearbyText()).append('\n'));
        return sb.toString();
    }
}
