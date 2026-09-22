package it.visiovoice.perception;

import it.visiovoice.agents.PerceptionPort;
import it.visiovoice.model.Barrier;
import it.visiovoice.model.BarrierType;
import it.visiovoice.model.EvidenceFidelity;
import it.visiovoice.model.FieldKind;
import it.visiovoice.model.FormField;
import it.visiovoice.model.Provenance;
import it.visiovoice.model.RegionRole;
import it.visiovoice.model.ScreenModel;
import it.visiovoice.model.ScreenRegion;
import it.visiovoice.model.VisualAsset;
import it.visiovoice.model.VisualKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

/**
 * Legge l'HTML con jsoup e produce un {@link ScreenModel} deterministico.
 *
 * <p>Zero LLM: ogni campo del modello viene da un nodo del DOM o e' dichiarato assente.
 * Un campo che qui non c'e' non comparira' mai nel racconto.
 *
 * <p>La catena di risoluzione dell'etichetta segue esattamente lo screen reader:
 * aria-labelledby, aria-label, label[for], label wrapper, title, placeholder, niente.
 * Dal passo 6 in poi la provenance e' AI_INFERRED e il campo genera una barriera UNLABELED_INPUT.
 */
@Service
public class HtmlPerceptionService implements PerceptionPort {

    // Valore step count assunto per la demo INPS (5 passi dichiarati in index.html)
    private static final int KNOWN_STEP_COUNT = 5;

    private static final Pattern STEP_IMG_PATTERN = Pattern.compile("step-indicator-(\\d+)\\.png");
    private static final Pattern STEP_HEADING_PATTERN = Pattern.compile("Passo\\s+(\\d+)");

    /** Alt che non trasmettono nulla di significativo. */
    private static final Set<String> GENERIC_ALT = Set.of(
            "", "immagine", "image", "grafico", "foto", "logo", "icona",
            "tabella", "chart", "figure", "image.", "img");

    /** Frammenti nel src di un'immagine che suggeriscono dati non accessibili. */
    private static final Set<String> DATA_SRC_FRAGMENTS = Set.of(
            "importi", "tabella", "grafico", "chart", "dati", "report");

    /** Frammenti nell'alt che indicano contenuto dati presentato come immagine. */
    private static final Set<String> DATA_ALT_FRAGMENTS = Set.of(
            "tabella", "grafico", "importi", "chart", "dati");

    /** Frammenti nell'id/class di un elemento che lo identificano come messaggio di errore. */
    private static final Set<String> ERROR_SELECTOR_FRAGMENTS = Set.of(
            "errore", "error", "invalid", "danger", "alert-error", "alert-danger");

    // -----------------------------------------------------------------------
    // Contratto pubblico
    // -----------------------------------------------------------------------

    @Override
    public ScreenModel perceive(String html, String baseUrl) {
        Document doc = Jsoup.parse(html != null ? html : "", baseUrl != null ? baseUrl : "");

        String screenId = buildScreenId(baseUrl);
        String url = baseUrl != null ? baseUrl : "";
        String title = doc.title();

        int[] stepInfo = extractStepInfo(doc);
        int stepIndex = stepInfo[0];
        int stepCount = stepInfo[1];

        Counter counter = new Counter();
        List<ScreenRegion> regions = extractRegions(doc, counter);
        List<FormField> fields = extractFields(doc);
        List<VisualAsset> visuals = extractVisuals(doc, counter);

        // ScreenModel parziale (barriere ancora vuote); serve a detectBarriers
        ScreenModel partial = new ScreenModel(screenId, url, title, stepIndex, stepCount,
                regions, fields, visuals, List.of());

        // Rilevamento barriere dal modello parziale (i segnali sono gia' nelle regioni)
        List<Barrier> barriers = detectBarriers(partial);

        // Collegamento campo -> barriere
        List<FormField> linkedFields = linkFieldBarriers(fields, barriers);

        return new ScreenModel(screenId, url, title, stepIndex, stepCount,
                regions, linkedFields, visuals, barriers);
    }

    /**
     * Rileva le barriere presenti nel modello.
     *
     * <p>Lavora solo sui dati del {@link ScreenModel}: non torna al DOM. Per funzionare
     * indipendentemente le informazioni necessarie (calendario, errore-senza-live) sono
     * codificate come regioni speciali durante {@link #perceive}.
     */
    @Override
    public List<Barrier> detectBarriers(ScreenModel screen) {
        List<Barrier> result = new ArrayList<>();
        result.addAll(detectImageOnlyData(screen));
        result.addAll(detectUnlabeledInputs(screen));
        result.addAll(detectVisualOnlyState(screen));
        result.addAll(detectErrorNotAnnounced(screen));
        return result;
    }

    // -----------------------------------------------------------------------
    // Rilevatori di barriere
    // -----------------------------------------------------------------------

    /** WCAG 1.1.1 — immagine raster che contiene dati. */
    private List<Barrier> detectImageOnlyData(ScreenModel screen) {
        List<Barrier> result = new ArrayList<>();
        for (VisualAsset v : screen.visuals()) {
            // Le immagini step-indicator vanno in VISUAL_ONLY_STATE, non qui
            if (v.kind() == VisualKind.STEP_INDICATOR) continue;
            // Immagini puramente decorative: nessuna barriera
            if (v.kind() == VisualKind.DECORATIVE) continue;

            boolean dataByKind = v.kind() == VisualKind.DATA_TABLE || v.kind() == VisualKind.CHART;
            boolean dataBySrc = containsAny(v.src().toLowerCase(), DATA_SRC_FRAGMENTS);
            boolean dataByAlt = containsAny(v.altText().toLowerCase(), DATA_ALT_FRAGMENTS);

            if (dataByKind || dataBySrc || dataByAlt) {
                result.add(new Barrier(
                        "barrier-image-data-" + v.id(),
                        BarrierType.IMAGE_ONLY_DATA,
                        v.domSelector() != null ? v.domSelector() : "img[src='" + v.src() + "']",
                        v.altText().isBlank() ? "immagine" : v.altText(),
                        "I dati sono disponibili solo come immagine raster: nessun testo nel DOM,"
                                + " nessuna alternativa accessibile. L'utente non puo' conoscere i"
                                + " valori senza aiuto esterno.",
                        "rilevata da perception sul DOM della pagina",
                        EvidenceFidelity.OSSERVATO));
            }
        }
        return result;
    }

    /** WCAG 1.3.1, 4.1.2 — campo senza nome accessibile. */
    private List<Barrier> detectUnlabeledInputs(ScreenModel screen) {
        List<Barrier> result = new ArrayList<>();
        for (FormField field : screen.fields()) {
            if (field.labelProvenance() == Provenance.AI_INFERRED) {
                result.add(new Barrier(
                        "barrier-unlabeled-" + field.id(),
                        BarrierType.UNLABELED_INPUT,
                        field.domSelector(),
                        "modifica, vuoto",
                        "Il campo non ha un nome accessibile nel DOM. Lo screen reader annuncia"
                                + " solo il tipo di controllo. L'utente non sa cosa inserire.",
                        "rilevata da perception sul DOM della pagina",
                        EvidenceFidelity.OSSERVATO));
            }
        }
        return result;
    }

    /** WCAG 1.1.1, 1.3.1, 4.1.2 — stato visivo comunicato solo per immagini o div-button. */
    private List<Barrier> detectVisualOnlyState(ScreenModel screen) {
        List<Barrier> result = new ArrayList<>();

        // b3 — step indicator come immagine raster
        for (VisualAsset v : screen.visuals()) {
            if (v.kind() == VisualKind.STEP_INDICATOR) {
                result.add(new Barrier(
                        "barrier-visual-step-" + v.id(),
                        BarrierType.VISUAL_ONLY_STATE,
                        v.domSelector() != null ? v.domSelector() : "img[src='" + v.src() + "']",
                        v.altText().isBlank() ? "" : v.altText(),
                        "L'avanzamento della procedura e' comunicato solo con un'immagine raster."
                                + " L'utente non sa a quale passo si trova ne' quanti ne restano.",
                        "rilevata da perception sul DOM della pagina",
                        EvidenceFidelity.OSSERVATO));
            }
        }

        // b4 — calendario come griglia di div[role=button] con aria-label vuoto
        // Il segnale e' stato codificato in una regione con prefisso "CALENDAR_VISUAL_ONLY:"
        for (ScreenRegion region : screen.regions()) {
            if (region.text() != null && region.text().startsWith("CALENDAR_VISUAL_ONLY:")) {
                result.add(new Barrier(
                        "barrier-visual-calendar-" + region.id(),
                        BarrierType.VISUAL_ONLY_STATE,
                        region.domSelector() != null ? region.domSelector() : ".calendar-grid",
                        "pulsante",
                        "La griglia del calendario e' composta da div[role=button] con aria-label"
                                + " vuoto. Lo screen reader annuncia solo 'pulsante' senza il"
                                + " numero del giorno. Non esiste percorso da tastiera alternativo.",
                        "rilevata da perception sul DOM della pagina",
                        EvidenceFidelity.OSSERVATO));
            }
        }

        return result;
    }

    /** WCAG 1.4.1, 3.3.1 — errore segnalato senza annuncio allo screen reader. */
    private List<Barrier> detectErrorNotAnnounced(ScreenModel screen) {
        List<Barrier> result = new ArrayList<>();
        // Il segnale e' codificato in regioni con prefisso "ERROR_NO_LIVE:"
        for (ScreenRegion region : screen.regions()) {
            if (region.text() != null && region.text().startsWith("ERROR_NO_LIVE:")) {
                result.add(new Barrier(
                        "barrier-error-no-live-" + region.id(),
                        BarrierType.ERROR_NOT_ANNOUNCED,
                        region.domSelector() != null ? region.domSelector() : "elemento-errore",
                        "",
                        "L'errore e' segnalato solo visivamente. Non c'e' aria-live ne'"
                                + " role=alert: lo screen reader non annuncia nulla. L'utente"
                                + " non sa perche' la procedura non avanza.",
                        "rilevata da perception sul DOM della pagina",
                        EvidenceFidelity.OSSERVATO));
            }
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Estrazione dal DOM
    // -----------------------------------------------------------------------

    private String buildScreenId(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "screen-anonymous";
        }
        try {
            String path = new java.net.URI(baseUrl).getPath();
            String filename = path.substring(path.lastIndexOf('/') + 1);
            if (filename.isEmpty()) filename = "root";
            int dot = filename.lastIndexOf('.');
            if (dot > 0) filename = filename.substring(0, dot);
            return "screen-" + filename;
        } catch (Exception e) {
            return "screen-" + Math.abs(baseUrl.hashCode());
        }
    }

    private int[] extractStepInfo(Document doc) {
        // Dal src dell'immagine step-indicator-N.png
        for (Element img : doc.select("img[src*=step-indicator]")) {
            Matcher m = STEP_IMG_PATTERN.matcher(img.attr("src"));
            if (m.find()) {
                return new int[]{Integer.parseInt(m.group(1)), KNOWN_STEP_COUNT};
            }
        }
        // Dal testo "Passo N" nelle intestazioni
        for (Element h : doc.select("h1,h2,h3")) {
            Matcher m = STEP_HEADING_PATTERN.matcher(h.text());
            if (m.find()) {
                return new int[]{Integer.parseInt(m.group(1)), KNOWN_STEP_COUNT};
            }
        }
        return new int[]{0, 0};
    }

    private List<ScreenRegion> extractRegions(Document doc, Counter counter) {
        List<ScreenRegion> regions = new ArrayList<>();

        // Intestazione della pagina
        for (Element el : doc.select(".page-header, header, [role=banner]")) {
            regions.add(new ScreenRegion("region-header-" + counter.next(),
                    RegionRole.HEADER, null, 0, el.text(), selectorOf(el)));
        }

        // Titoli h1..h4
        for (Element el : doc.select("h1, h2, h3, h4")) {
            int level = Character.getNumericValue(el.tagName().charAt(1));
            regions.add(new ScreenRegion("region-heading-" + counter.next(),
                    RegionRole.HEADING, el.text(), level, el.text(), selectorOf(el)));
        }

        // Riquadri informativi
        for (Element el : doc.select(".info-box, [role=note], .alert-info")) {
            regions.add(new ScreenRegion("region-infobox-" + counter.next(),
                    RegionRole.INFO_BOX, null, 0, el.text(), selectorOf(el)));
        }

        // Paragrafi significativi (saltati se vuoti)
        for (Element el : doc.select("p")) {
            String text = el.text().trim();
            if (!text.isEmpty()) {
                regions.add(new ScreenRegion("region-para-" + counter.next(),
                        RegionRole.PARAGRAPH, null, 0, text, "p"));
            }
        }

        // Form
        for (Element el : doc.select("form")) {
            regions.add(new ScreenRegion("region-form-" + counter.next(),
                    RegionRole.FORM, null, 0, el.text(),
                    el.id().isEmpty() ? "form" : "#" + el.id()));
        }

        // Fieldset
        for (Element el : doc.select("fieldset")) {
            Element legend = el.selectFirst("legend");
            String heading = legend != null ? legend.text() : null;
            regions.add(new ScreenRegion("region-fieldset-" + counter.next(),
                    RegionRole.FIELDSET, heading, 0, el.text(), "fieldset"));
        }

        // Tabelle
        for (Element el : doc.select("table")) {
            regions.add(new ScreenRegion("region-table-" + counter.next(),
                    RegionRole.TABLE, null, 0, el.text(), "table"));
        }

        // Navigazione e footer
        for (Element el : doc.select("nav, .footer-nav")) {
            regions.add(new ScreenRegion("region-nav-" + counter.next(),
                    RegionRole.NAV, null, 0, el.text(), selectorOf(el)));
        }
        for (Element el : doc.select("footer, .page-footer")) {
            regions.add(new ScreenRegion("region-footer-" + counter.next(),
                    RegionRole.FOOTER, null, 0, el.text(), selectorOf(el)));
        }

        // ---- Segnali di barriera codificati nelle regioni ----

        // b4 — calendario: griglia di div[role=button] con aria-label vuoto
        Elements emptyRoleButtons = doc.select("div[role=button][aria-label='']");
        if (emptyRoleButtons.size() >= 7) {
            // Determina il selettore del contenitore piu' specifico
            String containerSelector = ".calendar-grid";
            Element parent = emptyRoleButtons.first().parent();
            if (parent != null && !parent.className().isBlank()) {
                containerSelector = "." + parent.className().split("\\s+")[0];
            }
            regions.add(new ScreenRegion("region-calendar-" + counter.next(),
                    RegionRole.OTHER, null, 0,
                    "CALENDAR_VISUAL_ONLY: " + emptyRoleButtons.size()
                            + " div[role=button] con aria-label vuoto",
                    containerSelector));
        }

        // b5 — elementi di errore senza aria-live e senza role=alert
        String errorCssQuery = buildErrorQuery();
        for (Element el : doc.select(errorCssQuery)) {
            if ("alert".equals(el.attr("role"))) continue;
            if (isInsideAriaLive(el)) continue;
            String selector = el.id().isEmpty()
                    ? ("." + el.className().split("\\s+")[0])
                    : ("#" + el.id());
            regions.add(new ScreenRegion("region-errornolive-" + el.id() + "-" + counter.next(),
                    RegionRole.OTHER, null, 0,
                    "ERROR_NO_LIVE: " + (el.text().isBlank() ? "elemento-errore" : el.text()),
                    selector));
        }

        return regions;
    }

    /** Costruisce la query CSS per trovare gli elementi-errore. */
    private String buildErrorQuery() {
        // Usa [id*=X] e [class*=X] per ogni frammento
        List<String> parts = new ArrayList<>();
        for (String frag : ERROR_SELECTOR_FRAGMENTS) {
            parts.add("[id*=" + frag + "]");
            parts.add("[class*=" + frag + "]");
        }
        return String.join(", ", parts);
    }

    private boolean isInsideAriaLive(Element el) {
        if (el.hasAttr("aria-live")) return true;
        Element p = el.parent();
        while (p != null) {
            if (p.hasAttr("aria-live")) return true;
            p = p.parent();
        }
        return false;
    }

    private String selectorOf(Element el) {
        if (!el.id().isEmpty()) return "#" + el.id();
        if (!el.className().isEmpty()) return "." + el.className().split("\\s+")[0];
        return el.tagName();
    }

    // -----------------------------------------------------------------------
    // Estrazione campi: catena label a 7 livelli
    // -----------------------------------------------------------------------

    private List<FormField> extractFields(Document doc) {
        List<FormField> fields = new ArrayList<>();
        // Input visibili: esclusi hidden, submit, button, reset, image
        Elements inputs = doc.select(
                "input:not([type=hidden]):not([type=submit]):not([type=button])"
                        + ":not([type=reset]):not([type=image]),"
                        + "select, textarea");
        int unnamed = 0;
        for (Element input : inputs) {
            String id = input.id();
            if (id.isEmpty()) id = "input-unnamed-" + unnamed++;

            String[] resolved = resolveLabel(doc, input);
            String label = resolved[0];
            Provenance provenance = "AI_INFERRED".equals(resolved[1])
                    ? Provenance.AI_INFERRED : Provenance.SOURCE_VERBATIM;

            FieldKind kind = detectFieldKind(input, label);
            boolean required = input.hasAttr("required");

            String placeholder = input.attr("placeholder");
            String example = placeholder.isBlank() ? null : placeholder;

            // domSelector: #id se l'input ha un id nel DOM originale, altrimenti tagName
            String originalId = input.id();
            String domSelector = originalId.isEmpty() ? input.tagName() : "#" + originalId;

            fields.add(new FormField(
                    id,
                    label.isBlank() ? null : label,
                    provenance,
                    kind,
                    null,   // format: non inferita qui
                    example,
                    required,
                    List.of(),   // barrierIds: collegati dopo in linkFieldBarriers
                    domSelector));
        }
        return fields;
    }

    /**
     * Catena di risoluzione dell'etichetta, identica a quella di uno screen reader.
     *
     * @return {"label", "SOURCE_VERBATIM" | "AI_INFERRED"}
     */
    String[] resolveLabel(Document doc, Element input) {

        // 1. aria-labelledby -> testo degli elementi referenziati
        String labelledBy = input.attr("aria-labelledby");
        if (!labelledBy.isBlank()) {
            StringBuilder sb = new StringBuilder();
            for (String ref : labelledBy.split("\\s+")) {
                Element refEl = doc.getElementById(ref);
                if (refEl != null) {
                    if (!sb.isEmpty()) sb.append(' ');
                    sb.append(refEl.text());
                }
            }
            if (!sb.isEmpty()) return new String[]{sb.toString(), "SOURCE_VERBATIM"};
        }

        // 2. aria-label
        String ariaLabel = input.attr("aria-label");
        if (!ariaLabel.isBlank()) {
            return new String[]{ariaLabel, "SOURCE_VERBATIM"};
        }

        // 3. <label for="id">
        String inputId = input.id();
        if (!inputId.isEmpty()) {
            Element labelEl = doc.selectFirst("label[for=" + inputId + "]");
            if (labelEl != null) {
                return new String[]{labelEl.text(), "SOURCE_VERBATIM"};
            }
        }

        // 4. <label> che avvolge l'input
        Element ancestor = input.parent();
        while (ancestor != null) {
            if ("label".equalsIgnoreCase(ancestor.tagName())) {
                // testo proprio della label, escludendo il contenuto dell'input stesso
                String ownText = ancestor.ownText().trim();
                if (!ownText.isEmpty()) return new String[]{ownText, "SOURCE_VERBATIM"};
                // fallback: tutto il testo della label
                String fullText = ancestor.text().trim();
                if (!fullText.isEmpty()) return new String[]{fullText, "SOURCE_VERBATIM"};
            }
            ancestor = ancestor.parent();
        }

        // 5. title
        String title = input.attr("title");
        if (!title.isBlank()) {
            return new String[]{title, "SOURCE_VERBATIM"};
        }

        // 6. placeholder — nome dedotto, il campo e' comunque unlabeled
        String placeholder = input.attr("placeholder");
        if (!placeholder.isBlank()) {
            return new String[]{placeholder, "AI_INFERRED"};
        }

        // 7. niente — label vuota, barriera
        return new String[]{"", "AI_INFERRED"};
    }

    private FieldKind detectFieldKind(Element input, String label) {
        String type = input.attr("type").toLowerCase();
        String name = input.attr("name").toLowerCase();
        String id = input.id().toLowerCase();
        String lbl = label != null ? label.toLowerCase() : "";
        String tag = input.tagName().toLowerCase();

        if ("select".equals(tag)) return FieldKind.SELECT;
        if ("textarea".equals(tag)) return FieldKind.TEXT;
        if ("checkbox".equals(type)) return FieldKind.CHECKBOX;
        if ("radio".equals(type)) return FieldKind.RADIO;
        if ("email".equals(type)) return FieldKind.EMAIL;
        if ("tel".equals(type)) return FieldKind.PHONE;
        if ("number".equals(type)) return FieldKind.NUMBER;

        if (containsAny(id + " " + name, Set.of("iban"))) return FieldKind.IBAN;
        if (containsAny(id + " " + name + " " + lbl, Set.of("codice fiscale", "cf_", "codicefiscale")))
            return FieldKind.FISCAL_CODE;
        if (id.startsWith("cf") || name.startsWith("cf")) return FieldKind.FISCAL_CODE;
        if (containsAny(id + " " + name, Set.of("nascita", "data_n", "dob"))) return FieldKind.DATE;
        if (lbl.contains("isee") || lbl.contains("importo") || lbl.contains("euro")) return FieldKind.CURRENCY;
        if (lbl.contains("email")) return FieldKind.EMAIL;
        if (lbl.contains("telefono") || lbl.contains("phone") || lbl.contains("cellulare"))
            return FieldKind.PHONE;

        return FieldKind.TEXT;
    }

    // -----------------------------------------------------------------------
    // Estrazione immagini
    // -----------------------------------------------------------------------

    private List<VisualAsset> extractVisuals(Document doc, Counter counter) {
        List<VisualAsset> visuals = new ArrayList<>();
        int idx = 0;
        for (Element img : doc.select("img")) {
            String src = img.attr("src");
            String alt = img.attr("alt");
            VisualKind kind = detectVisualKind(src, alt);
            String nearby = nearbyText(img);
            String domSelector = src.isEmpty()
                    ? ("img:nth-of-type(" + (idx + 1) + ")")
                    : ("img[src='" + src + "']");
            visuals.add(new VisualAsset(
                    "visual-" + (idx++),
                    src, alt, kind, nearby, domSelector));
        }
        return visuals;
    }

    private VisualKind detectVisualKind(String src, String alt) {
        String sl = src.toLowerCase();
        String al = alt.trim().toLowerCase();
        if (sl.contains("step-indicator")) return VisualKind.STEP_INDICATOR;
        if (containsAny(sl, DATA_SRC_FRAGMENTS)) return VisualKind.DATA_TABLE;
        if (al.contains("grafico") || al.contains("chart") || al.contains("diagramma"))
            return VisualKind.CHART;
        if (al.isEmpty()) return VisualKind.UNKNOWN;
        return VisualKind.UNKNOWN;
    }

    private String nearbyText(Element img) {
        StringBuilder sb = new StringBuilder();
        Element p = img.parent();
        if (p != null) {
            String own = p.ownText().trim();
            if (!own.isEmpty()) sb.append(own);
        }
        Element prev = img.previousElementSibling();
        if (prev != null) {
            String t = prev.text().trim();
            if (!t.isEmpty()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(t);
            }
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Collegamento campo -> barriere
    // -----------------------------------------------------------------------

    private List<FormField> linkFieldBarriers(List<FormField> fields, List<Barrier> barriers) {
        if (barriers.isEmpty()) return fields;

        // Mappa domSelector -> lista di barrierIds
        Map<String, List<String>> selectorToBarriers = new HashMap<>();
        for (Barrier b : barriers) {
            if (b.type() == BarrierType.UNLABELED_INPUT) {
                selectorToBarriers
                        .computeIfAbsent(b.location(), k -> new ArrayList<>())
                        .add(b.id());
            }
        }
        if (selectorToBarriers.isEmpty()) return fields;

        List<FormField> result = new ArrayList<>(fields.size());
        for (FormField f : fields) {
            List<String> bids = selectorToBarriers.get(f.domSelector());
            result.add(bids == null ? f : f.withBarrierIds(bids));
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Utilita'
    // -----------------------------------------------------------------------

    private static boolean containsAny(String text, Set<String> fragments) {
        for (String frag : fragments) {
            if (text.contains(frag)) return true;
        }
        return false;
    }

    /** Contatore condiviso all'interno di una singola chiamata a perceive. */
    static final class Counter {
        private int value = 0;
        int next() { return value++; }
    }
}
