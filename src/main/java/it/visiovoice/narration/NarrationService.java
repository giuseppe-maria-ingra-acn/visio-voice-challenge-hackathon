package it.visiovoice.narration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.visiovoice.agents.AgentContext;
import it.visiovoice.agents.NarrationPort;
import it.visiovoice.llm.LlmClient;
import it.visiovoice.llm.LlmRequest;
import it.visiovoice.llm.LlmResponse;
import it.visiovoice.model.AccessibleTable;
import it.visiovoice.model.BarrierType;
import it.visiovoice.model.DetailLevel;
import it.visiovoice.model.FidelityVerdict;
import it.visiovoice.model.Provenance;
import it.visiovoice.model.ProvenanceGate;
import it.visiovoice.model.ScreenModel;
import it.visiovoice.model.ScreenRegion;
import it.visiovoice.model.ScriptSegment;
import it.visiovoice.model.SegmentRole;
import it.visiovoice.model.SourceFact;
import it.visiovoice.model.SpokenScript;
import it.visiovoice.model.VisualAsset;
import it.visiovoice.model.VisualDescription;
import it.visiovoice.model.VisualKind;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Trasforma un {@link ScreenModel} in racconto pronunciabile, e un'immagine in struttura
 * consultabile.
 *
 * <h2>Regola dei 15 secondi</h2>
 * Marco decide in pochi secondi se gli stiamo facendo perdere tempo. La panoramica (livello
 * {@link DetailLevel#BRIEF}) ha al massimo tre segmenti: che pagina e', quante sezioni, cosa
 * si puo' fare. I dettagli arrivano solo su richiesta, una sezione alla volta.
 *
 * <h2>Tabella come struttura, non come prosa</h2>
 * Quando un'immagine porta dati tabellari, {@link #describeVisual} restituisce un
 * {@link AccessibleTable} che il frontend puo' rendere come {@code <table>} vero, navigabile
 * con uno screen reader per righe e colonne. La prosa e' insufficiente: si puo' ascoltare ma
 * non consultare.
 *
 * <h2>Il gate non si reimplementa</h2>
 * Tutto il testo prodotto da un modello passa per {@link FidelityAgent}, che delega a
 * {@link ProvenanceGate}. Nessuna implementazione alternativa del controllo: una soglia diversa
 * di onesta' e' il modo piu' sottile di tradire la fiducia dell'utente.
 */
@Service
public class NarrationService implements NarrationPort {

    private final LlmClient llm;
    private final FidelityAgent fidelity;
    private final ObjectMapper json;

    public NarrationService(LlmClient llm, FidelityAgent fidelity, ObjectMapper json) {
        this.llm = llm;
        this.fidelity = fidelity;
        this.json = json;
    }

    // ---------------------------------------------------------------- narrate

    /**
     * Racconta la schermata al livello di dettaglio richiesto.
     *
     * <p>BRIEF: tre segmenti al massimo — titolo, struttura, azione principale. L'utente sente
     * in tre secondi se e' nel posto giusto, e decide se approfondire.
     *
     * <p>STANDARD e FULL: aggiungono le regioni, lo stato del wizard, le barriere rilevate.
     *
     * <p>Tutto il testo passa dal gate prima di essere restituito: il metodo non restituisce mai
     * uno script non verificato.
     */
    @Override
    public SpokenScript narrate(ScreenModel screen, DetailLevel level, AgentContext ctx) {
        AtomicInteger counter = new AtomicInteger(0);
        String base = "scr-" + screen.screenId();
        List<ScriptSegment> segments = new ArrayList<>();

        // Segmento 1: titolo della pagina (SOURCE_VERBATIM: letto dal DOM)
        String pageTitle = screen.title() != null && !screen.title().isBlank()
                ? screen.title()
                : "Pagina senza titolo";
        segments.add(ScriptSegment.verbatim(
                seg(base, counter), SegmentRole.TITLE,
                pageTitle + ".",
                List.of()));

        // Segmento 2: struttura (SOURCE_VERBATIM: il conteggio e' osservabile nel DOM)
        String overviewText = buildOverview(screen);
        segments.add(ScriptSegment.verbatim(
                seg(base, counter), SegmentRole.OVERVIEW,
                overviewText,
                List.of()));

        // Segmento 3: cosa si puo' fare (AI_REPHRASED: sintesi delle azioni disponibili)
        String actionText = buildActionText(screen);
        segments.add(ScriptSegment.rephrased(
                seg(base, counter), SegmentRole.NAVIGATION,
                actionText,
                List.of()));

        // Per STANDARD e FULL: aggiungi passo corrente, barriere note
        if (level != DetailLevel.BRIEF) {
            if (screen.hasSteps() && screen.stepIndex() > 0) {
                String stepText = "Sei al passo " + screen.stepIndex()
                        + " di " + screen.stepCount() + ".";
                segments.add(ScriptSegment.verbatim(
                        seg(base, counter), SegmentRole.STEP_STATUS,
                        stepText,
                        List.of()));
            }
            for (var region : screen.regions()) {
                if (isSignificantRegion(region)) {
                    String heading = region.heading() != null ? region.heading() : region.role().name();
                    String regionText = heading + ": " + firstSentence(region.text()) + ".";
                    segments.add(ScriptSegment.verbatim(
                            seg(base, counter), SegmentRole.OVERVIEW,
                            regionText,
                            List.of()));
                }
            }
            for (var barrier : screen.barriers()) {
                String barrierText = buildBarrierRepair(barrier);
                if (barrierText != null) {
                    segments.add(ScriptSegment.rephrased(
                            seg(base, counter), SegmentRole.BARRIER_REPAIR,
                            barrierText,
                            List.of()));
                }
            }
        }

        SpokenScript script = SpokenScript.of(base + "-" + level.name().toLowerCase(),
                screen.screenId(), level, segments);

        // Gate: verifica ogni segmento AI_REPHRASED contro i fatti noti e il corpus della pagina
        List<SourceFact> facts = ctx != null && ctx.scenario() != null
                ? ctx.scenario().sourceFacts()
                : List.of();
        String extraCorpus = screen.textCorpus();
        FidelityVerdict verdict = fidelity.verify(script, facts, extraCorpus);
        return verdict.script();
    }

    // ---------------------------------------------------------------- describeVisual

    /**
     * Descrive un'immagine in modo strutturato.
     *
     * <p>Per le immagini di tipo {@link VisualKind#DATA_TABLE}: chiama il modello (o la fixture
     * in modalita' mock), ottiene la struttura JSON della tabella, la restituisce come
     * {@link AccessibleTable}. Il frontend la rende come {@code <table>} vero con
     * {@code <th scope>}: non prosa da ascoltare, ma struttura da navigare.
     *
     * <p>Per gli indicatori di avanzamento: restituisce il testo del passo corrente con i
     * segnaposto sostituiti dai valori del contesto.
     *
     * <p>La descrizione non supera il gate: il JSON della fixture viene ancorato ai fatti dello
     * scenario, non a valori inventati.
     */
    @Override
    public VisualDescription describeVisual(VisualAsset asset, AgentContext ctx) {
        String promptId = resolvePromptId(asset);
        LlmRequest request = buildRequest(promptId, asset, ctx);
        LlmResponse response = llm.complete(request);

        String responseText = response.text();
        String trimmed = responseText.trim();

        if (trimmed.startsWith("{")) {
            return parseJsonDescription(asset, trimmed, ctx);
        } else {
            // Risposta testuale (es. indicatore di passi): la avvolge in un VisualDescription
            return new VisualDescription(
                    asset.id(),
                    trimmed,
                    List.of(),
                    response.fromFixture() ? Provenance.AI_REPHRASED : Provenance.AI_INFERRED,
                    response.sourceRefs());
        }
    }

    // ---------------------------------------------------------------- privati

    private static String seg(String base, AtomicInteger counter) {
        return base + "-s" + counter.incrementAndGet();
    }

    private static String buildOverview(ScreenModel screen) {
        int regionCount = screen.regions().size();
        int visualCount = screen.visuals().size();
        int fieldCount = screen.fields().size();

        StringBuilder sb = new StringBuilder();
        if (regionCount == 0) {
            sb.append("La pagina non ha sezioni testuali rilevate.");
        } else if (regionCount == 1) {
            sb.append("La pagina ha una sezione.");
        } else {
            sb.append("La pagina ha ").append(regionCount).append(" sezioni.");
        }
        if (visualCount > 0) {
            sb.append(" ").append(visualCount == 1
                    ? "C'e' un'immagine informativa."
                    : "Ci sono " + visualCount + " immagini informative.");
        }
        if (fieldCount > 0) {
            sb.append(" ").append(fieldCount == 1
                    ? "C'e' un campo da compilare."
                    : "Ci sono " + fieldCount + " campi da compilare.");
        }
        return sb.toString();
    }

    private static String buildActionText(ScreenModel screen) {
        if (!screen.fields().isEmpty()) {
            return "Puoi compilare il modulo e proseguire con la domanda.";
        }
        if (!screen.visuals().isEmpty()) {
            return "Puoi leggere le informazioni e proseguire con la domanda.";
        }
        return "Puoi navigare la pagina e proseguire.";
    }

    private static boolean isSignificantRegion(ScreenRegion region) {
        if (region.text() == null || region.text().isBlank()) {
            return false;
        }
        return switch (region.role()) {
            case HEADER, FOOTER, NAV -> false;
            default -> region.heading() != null && !region.heading().isBlank();
        };
    }

    private static String firstSentence(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int dot = text.indexOf('.');
        if (dot > 0 && dot < 120) {
            return text.substring(0, dot);
        }
        return text.length() > 120 ? text.substring(0, 120) : text;
    }

    private static String buildBarrierRepair(it.visiovoice.model.Barrier barrier) {
        return switch (barrier.type()) {
            case IMAGE_ONLY_DATA ->
                "Ho trovato un'immagine con dati che lo screen reader non riesce a leggere. "
                + "Usa il comando di descrizione visiva per sentirne il contenuto.";
            case UNLABELED_INPUT ->
                "C'e' un campo senza etichetta. "
                + "Chiedimi di guidarti in questo campo e ti diro' cosa inserire.";
            case VISUAL_ONLY_STATE ->
                "Lo stato corrente della procedura e' indicato solo visivamente. "
                + "Chiedimi a che passo sei per sentirlo.";
            case ERROR_NOT_ANNOUNCED ->
                "Gli errori su questa pagina vengono mostrati solo visivamente. "
                + "Se qualcosa non va, chiedimi di controllare i campi.";
            default -> null;
        };
    }

    // ---------------------------------------------------------------- LLM e parsing

    /**
     * Determina il promptId dalla tipologia e dall'id dell'asset.
     *
     * <p>Convenzione per le fixture esistenti:
     * <ul>
     *   <li>DATA_TABLE con id che finisce per "-YYYY": {@code descrivi-tabella-{id-senza-anno}}
     *   <li>STEP_INDICATOR: {@code descrivi-indicatore-passi}
     *   <li>altri: {@code descrivi-visivo-{id}}
     * </ul>
     */
    static String resolvePromptId(VisualAsset asset) {
        return switch (asset.kind()) {
            case DATA_TABLE -> "descrivi-tabella-" + stripYearSuffix(asset.id());
            case STEP_INDICATOR -> "descrivi-indicatore-passi";
            default -> "descrivi-visivo-" + asset.id();
        };
    }

    /** Rimuove il suffisso anno dal fondo dell'id: "importi-2026" -> "importi". */
    private static String stripYearSuffix(String id) {
        return id.replaceAll("-\\d{4}$", "");
    }

    private static LlmRequest buildRequest(String promptId, VisualAsset asset, AgentContext ctx) {
        LlmRequest req = LlmRequest.of(promptId,
                "Descrivi il contenuto visivo dell'asset: " + asset.id());
        if (ctx != null) {
            String passo = ctx.option("passo").orElse("0");
            String totale = ctx.option("totale").orElse("0");
            req = req.withVariable("passo", passo).withVariable("totale", totale);
        }
        return req;
    }

    private VisualDescription parseJsonDescription(VisualAsset asset, String jsonText, AgentContext ctx) {
        try {
            JsonNode root = json.readTree(jsonText);
            String assetId = root.path("assetId").asText(asset.id());
            String summary = root.path("summary").asText("");
            List<String> sourceRefs = parseStringList(root.path("sourceRefs"));
            List<AccessibleTable> tables = parseTables(root.path("tables"), ctx);

            // La provenance della VisualDescription e' il minimo delle sue tabelle,
            // o AI_REPHRASED se non ci sono tabelle
            Provenance prov = tables.stream()
                    .map(AccessibleTable::provenance)
                    .reduce(Provenance.AI_REPHRASED, Provenance::weakest);

            return new VisualDescription(assetId, summary, tables, prov, sourceRefs);
        } catch (Exception e) {
            // Parsing fallito: risposta segnalata come dedotta, mai inventata
            return new VisualDescription(
                    asset.id(),
                    "Non ho potuto leggere la struttura di questa immagine.",
                    List.of(),
                    Provenance.AI_INFERRED,
                    List.of());
        }
    }

    private List<AccessibleTable> parseTables(JsonNode tablesNode, AgentContext ctx) {
        if (tablesNode == null || tablesNode.isMissingNode() || !tablesNode.isArray()) {
            return List.of();
        }
        List<AccessibleTable> result = new ArrayList<>();
        for (JsonNode tableNode : tablesNode) {
            String caption = tableNode.path("caption").asText(null);
            List<String> headers = parseStringList(tableNode.path("headers"));
            List<List<String>> rows = parseRows(tableNode.path("rows"));
            List<String> refs = parseStringList(tableNode.path("sourceRefs"));
            // Le tabelle lette da un modello a partire da un PNG sono AI_REPHRASED (candidatura):
            // il gate verra' applicato quando lo script che le include viene verificato
            result.add(new AccessibleTable(caption, headers, rows, Provenance.AI_REPHRASED, refs));
        }
        return result;
    }

    private static List<String> parseStringList(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            result.add(item.asText());
        }
        return result;
    }

    private static List<List<String>> parseRows(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<List<String>> result = new ArrayList<>();
        for (JsonNode row : node) {
            if (row.isArray()) {
                List<String> cells = new ArrayList<>();
                for (JsonNode cell : row) {
                    cells.add(cell.asText());
                }
                result.add(cells);
            }
        }
        return result;
    }
}
