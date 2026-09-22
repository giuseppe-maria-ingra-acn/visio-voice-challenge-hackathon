package it.visiovoice.perception;

import it.visiovoice.model.Barrier;
import it.visiovoice.model.BarrierType;
import it.visiovoice.model.FieldKind;
import it.visiovoice.model.FormField;
import it.visiovoice.model.Provenance;
import it.visiovoice.model.RegionRole;
import it.visiovoice.model.ScreenModel;
import it.visiovoice.model.ScreenRegion;
import it.visiovoice.model.VisualAsset;
import it.visiovoice.model.VisualKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica dell'implementazione a sette livelli della catena label e del rilevamento
 * di tutte le barriere dichiarate nello scenario.
 *
 * <p>Tutti i test operano su HTML statico in memoria o nelle risorse di test: zero rete,
 * zero LLM, zero timing.
 */
class HtmlPerceptionServiceTest {

    private HtmlPerceptionService service;

    @BeforeEach
    void setUp() {
        service = new HtmlPerceptionService();
    }

    // =======================================================================
    // Utilita'
    // =======================================================================

    /** Legge un file dalla cartella src/test/resources/html/. */
    private String loadHtml(String filename) throws IOException {
        try (var is = getClass().getResourceAsStream("/html/" + filename)) {
            if (is == null) throw new IOException("File non trovato: " + filename);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ScreenModel perceiveHtml(String filename) throws IOException {
        String html = loadHtml(filename);
        return service.perceive(html, "http://localhost:8080/demo/" + filename);
    }

    /** HTML minimale che contiene un solo input con le proprieta' date. */
    private static String inputHtml(String inputTag) {
        return "<html><body><form>" + inputTag + "</form></body></html>";
    }

    // =======================================================================
    // Catena label: 7 posizioni (tutte SOURCE_VERBATIM tranne 6 e 7)
    // =======================================================================

    @Nested
    @DisplayName("Catena label — 7 posizioni")
    class LabelChainTest {

        /**
         * Posizione 1: aria-labelledby punta a un elemento nel DOM.
         * Provenance attesa: SOURCE_VERBATIM.
         */
        @Test
        @DisplayName("1 — aria-labelledby")
        void ariaLabelledBy() {
            String html = "<html><body><form>"
                    + "<span id='lbl1'>Codice fiscale</span>"
                    + "<input id='cf' aria-labelledby='lbl1'>"
                    + "</form></body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/test");

            FormField field = findField(screen, "cf");
            assertThat(field.label()).isEqualTo("Codice fiscale");
            assertThat(field.labelProvenance()).isEqualTo(Provenance.SOURCE_VERBATIM);
        }

        /**
         * Posizione 2: aria-label sull'input.
         * Provenance attesa: SOURCE_VERBATIM.
         */
        @Test
        @DisplayName("2 — aria-label")
        void ariaLabel() {
            String html = inputHtml("<input id='nome' aria-label='Nome del richiedente'>");
            ScreenModel screen = service.perceive(html, "http://localhost/test");

            FormField field = findField(screen, "nome");
            assertThat(field.label()).isEqualTo("Nome del richiedente");
            assertThat(field.labelProvenance()).isEqualTo(Provenance.SOURCE_VERBATIM);
        }

        /**
         * Posizione 3: &lt;label for="id"&gt; collegata all'input.
         * Provenance attesa: SOURCE_VERBATIM.
         */
        @Test
        @DisplayName("3 — label[for]")
        void labelFor() {
            String html = "<html><body><form>"
                    + "<label for='cognome'>Cognome *</label>"
                    + "<input id='cognome' type='text'>"
                    + "</form></body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/test");

            FormField field = findField(screen, "cognome");
            assertThat(field.label()).isEqualTo("Cognome *");
            assertThat(field.labelProvenance()).isEqualTo(Provenance.SOURCE_VERBATIM);
        }

        /**
         * Posizione 4: &lt;label&gt; che avvolge l'input.
         * Provenance attesa: SOURCE_VERBATIM.
         */
        @Test
        @DisplayName("4 — label che avvolge l'input")
        void labelWrapper() {
            String html = "<html><body><form>"
                    + "<label>Data di nascita <input id='dob' type='text'></label>"
                    + "</form></body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/test");

            FormField field = findField(screen, "dob");
            assertThat(field.label()).isEqualTo("Data di nascita");
            assertThat(field.labelProvenance()).isEqualTo(Provenance.SOURCE_VERBATIM);
        }

        /**
         * Posizione 5: attributo title sull'input (nessun altro metodo disponibile).
         * Provenance attesa: SOURCE_VERBATIM.
         */
        @Test
        @DisplayName("5 — title")
        void titleAttribute() {
            String html = inputHtml("<input id='isee' type='text' title='Valore ISEE in euro'>");
            ScreenModel screen = service.perceive(html, "http://localhost/test");

            FormField field = findField(screen, "isee");
            assertThat(field.label()).isEqualTo("Valore ISEE in euro");
            assertThat(field.labelProvenance()).isEqualTo(Provenance.SOURCE_VERBATIM);
        }

        /**
         * Posizione 6: solo placeholder disponibile.
         * Provenance attesa: AI_INFERRED (il placeholder scompare durante la digitazione).
         * Effetto collaterale: barriera UNLABELED_INPUT.
         */
        @Test
        @DisplayName("6 — placeholder (AI_INFERRED + UNLABELED_INPUT)")
        void placeholder() {
            String html = inputHtml(
                    "<input id='iban' type='text' placeholder='es. IT60X0542811101000000123456'>");
            ScreenModel screen = service.perceive(html, "http://localhost/test");

            FormField field = findField(screen, "iban");
            assertThat(field.label()).isEqualTo("es. IT60X0542811101000000123456");
            assertThat(field.labelProvenance()).isEqualTo(Provenance.AI_INFERRED);
            assertThat(field.hasInferredLabel()).isTrue();

            assertThat(screen.barriers())
                    .anyMatch(b -> b.type() == BarrierType.UNLABELED_INPUT);
        }

        /**
         * Posizione 7: nessun metodo disponibile.
         * Provenance attesa: AI_INFERRED, label null.
         * Effetto collaterale: barriera UNLABELED_INPUT.
         */
        @Test
        @DisplayName("7 — nessuna label (AI_INFERRED + UNLABELED_INPUT)")
        void noLabel() {
            String html = inputHtml("<input id='xyz' type='text'>");
            ScreenModel screen = service.perceive(html, "http://localhost/test");

            FormField field = findField(screen, "xyz");
            assertThat(field.label()).isNull();
            assertThat(field.labelProvenance()).isEqualTo(Provenance.AI_INFERRED);

            assertThat(screen.barriers())
                    .anyMatch(b -> b.type() == BarrierType.UNLABELED_INPUT);
        }

        /**
         * Priorita': aria-labelledby batte aria-label batte label[for].
         */
        @Test
        @DisplayName("Priorita' — aria-labelledby vince su aria-label e label[for]")
        void priorityAriaLabelledByWins() {
            String html = "<html><body><form>"
                    + "<span id='refspan'>Etichetta via labelledby</span>"
                    + "<label for='campo'>Etichetta via label-for</label>"
                    + "<input id='campo' aria-labelledby='refspan' aria-label='Etichetta via aria-label'>"
                    + "</form></body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/test");

            FormField field = findField(screen, "campo");
            assertThat(field.label()).isEqualTo("Etichetta via labelledby");
            assertThat(field.labelProvenance()).isEqualTo(Provenance.SOURCE_VERBATIM);
        }

        private FormField findField(ScreenModel screen, String id) {
            return screen.field(id).orElseThrow(
                    () -> new AssertionError("Campo '" + id + "' non trovato nel ScreenModel"));
        }
    }

    // =======================================================================
    // Rilevamento barriere — test positivi e negativi
    // =======================================================================

    @Nested
    @DisplayName("Barriere — positivi e negativi")
    class BarrierDetectionTest {

        // --- IMAGE_ONLY_DATA ---

        @Test
        @DisplayName("IMAGE_ONLY_DATA — positivo: src contiene 'importi'")
        void imageOnlyData_positive_src() {
            String html = "<html><body>"
                    + "<img src='importi-2026.png' alt='tabella importi'>"
                    + "</body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.barriers())
                    .anyMatch(b -> b.type() == BarrierType.IMAGE_ONLY_DATA);
        }

        @Test
        @DisplayName("IMAGE_ONLY_DATA — positivo: alt contiene 'tabella'")
        void imageOnlyData_positive_alt() {
            String html = "<html><body>"
                    + "<img src='dati.png' alt='tabella degli importi mensili'>"
                    + "</body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.barriers())
                    .anyMatch(b -> b.type() == BarrierType.IMAGE_ONLY_DATA);
        }

        @Test
        @DisplayName("IMAGE_ONLY_DATA — negativo: immagine con alt descrittivo e src neutro")
        void imageOnlyData_negative() {
            String html = "<html><body>"
                    + "<img src='foto-sede.jpg' alt='La sede di Roma in Via del Corso'>"
                    + "</body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.barriers())
                    .noneMatch(b -> b.type() == BarrierType.IMAGE_ONLY_DATA);
        }

        @Test
        @DisplayName("IMAGE_ONLY_DATA — negativo: nessuna immagine")
        void imageOnlyData_negative_noImage() {
            String html = "<html><body><p>Solo testo.</p></body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.barriers())
                    .noneMatch(b -> b.type() == BarrierType.IMAGE_ONLY_DATA);
        }

        // --- UNLABELED_INPUT ---

        @Test
        @DisplayName("UNLABELED_INPUT — positivo: input senza label (solo placeholder)")
        void unlabeledInput_positive_placeholder() {
            String html = "<html><body><form>"
                    + "<input id='iban' placeholder='es. IT60X...'>"
                    + "</form></body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.barriers())
                    .anyMatch(b -> b.type() == BarrierType.UNLABELED_INPUT);
        }

        @Test
        @DisplayName("UNLABELED_INPUT — positivo: input completamente senza label")
        void unlabeledInput_positive_noLabel() {
            String html = "<html><body><form>"
                    + "<input id='campo' type='text'>"
                    + "</form></body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.barriers())
                    .anyMatch(b -> b.type() == BarrierType.UNLABELED_INPUT);
        }

        @Test
        @DisplayName("UNLABELED_INPUT — negativo: input con label[for]")
        void unlabeledInput_negative_labelFor() {
            String html = "<html><body><form>"
                    + "<label for='nome'>Nome</label>"
                    + "<input id='nome' type='text'>"
                    + "</form></body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.barriers())
                    .noneMatch(b -> b.type() == BarrierType.UNLABELED_INPUT);
        }

        @Test
        @DisplayName("UNLABELED_INPUT — negativo: input con aria-label")
        void unlabeledInput_negative_ariaLabel() {
            String html = "<html><body><form>"
                    + "<input id='cf' aria-label='Codice fiscale'>"
                    + "</form></body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.barriers())
                    .noneMatch(b -> b.type() == BarrierType.UNLABELED_INPUT);
        }

        // --- VISUAL_ONLY_STATE (step indicator) ---

        @Test
        @DisplayName("VISUAL_ONLY_STATE — positivo: step-indicator-N.png con alt generico")
        void visualOnlyState_positive_stepIndicator() {
            String html = "<html><body>"
                    + "<img src='step-indicator-3.png' alt='immagine'>"
                    + "</body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.barriers())
                    .anyMatch(b -> b.type() == BarrierType.VISUAL_ONLY_STATE);
        }

        @Test
        @DisplayName("VISUAL_ONLY_STATE — negativo: nessuna immagine step-indicator")
        void visualOnlyState_negative_noStepIndicator() {
            String html = "<html><body><p>Nessun indicatore.</p></body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.barriers())
                    .noneMatch(b -> b.type() == BarrierType.VISUAL_ONLY_STATE);
        }

        // --- VISUAL_ONLY_STATE (calendario) ---

        @Test
        @DisplayName("VISUAL_ONLY_STATE — positivo: griglia calendario con div[role=button][aria-label='']")
        void visualOnlyState_positive_calendar() {
            // Griglia minimale: 7 div[role=button] con aria-label vuoto
            StringBuilder grid = new StringBuilder("<html><body><div class='calendar-grid'>");
            for (int i = 1; i <= 7; i++) {
                grid.append("<div role='button' aria-label=''>").append(i).append("</div>");
            }
            grid.append("</div></body></html>");

            ScreenModel screen = service.perceive(grid.toString(), "http://localhost/");
            assertThat(screen.barriers())
                    .anyMatch(b -> b.type() == BarrierType.VISUAL_ONLY_STATE);
        }

        @Test
        @DisplayName("VISUAL_ONLY_STATE — negativo: nessuna griglia calendario")
        void visualOnlyState_negative_noCalendar() {
            String html = "<html><body><form>"
                    + "<label for='data'>Data</label>"
                    + "<input id='data' type='date'>"
                    + "</form></body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            // Nessuna barriera VISUAL_ONLY_STATE (nessuno step indicator, nessun calendario)
            assertThat(screen.barriers())
                    .noneMatch(b -> b.type() == BarrierType.VISUAL_ONLY_STATE);
        }

        @Test
        @DisplayName("VISUAL_ONLY_STATE — negativo: meno di 7 div[role=button]")
        void visualOnlyState_negative_fewButtons() {
            String html = "<html><body>"
                    + "<div role='button' aria-label=''>1</div>"
                    + "<div role='button' aria-label=''>2</div>"
                    + "</body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.barriers())
                    .noneMatch(b -> b.type() == BarrierType.VISUAL_ONLY_STATE);
        }

        // --- ERROR_NOT_ANNOUNCED ---

        @Test
        @DisplayName("ERROR_NOT_ANNOUNCED — positivo: div#errore-iban senza aria-live ne' role=alert")
        void errorNotAnnounced_positive() {
            String html = "<html><body><form>"
                    + "<input id='iban' type='text'>"
                    + "<div id='errore-iban' style='display:none;color:red'>"
                    + "Verificare il codice IBAN inserito.</div>"
                    + "</form></body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.barriers())
                    .anyMatch(b -> b.type() == BarrierType.ERROR_NOT_ANNOUNCED);
        }

        @Test
        @DisplayName("ERROR_NOT_ANNOUNCED — negativo: div#errore con role=alert")
        void errorNotAnnounced_negative_roleAlert() {
            String html = "<html><body><form>"
                    + "<input id='campo' type='text'>"
                    + "<div id='errore-campo' role='alert'>Errore nel campo.</div>"
                    + "</form></body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.barriers())
                    .noneMatch(b -> b.type() == BarrierType.ERROR_NOT_ANNOUNCED);
        }

        @Test
        @DisplayName("ERROR_NOT_ANNOUNCED — negativo: div#errore dentro un aria-live")
        void errorNotAnnounced_negative_insideAriaLive() {
            String html = "<html><body>"
                    + "<div aria-live='polite'>"
                    + "<div id='errore-campo'>Errore nel campo.</div>"
                    + "</div>"
                    + "</body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.barriers())
                    .noneMatch(b -> b.type() == BarrierType.ERROR_NOT_ANNOUNCED);
        }

        @Test
        @DisplayName("ERROR_NOT_ANNOUNCED — negativo: nessun elemento errore")
        void errorNotAnnounced_negative_noError() {
            String html = "<html><body><p>Tutto ok.</p></body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.barriers())
                    .noneMatch(b -> b.type() == BarrierType.ERROR_NOT_ANNOUNCED);
        }
    }

    // =======================================================================
    // detectBarriers(ScreenModel) — indipendente da perceive
    // =======================================================================

    @Nested
    @DisplayName("detectBarriers(ScreenModel) — indipendente")
    class DetectBarriersIndependentTest {

        @Test
        @DisplayName("IMAGE_ONLY_DATA da VisualAsset kind=DATA_TABLE")
        void detectFromVisualKindDataTable() {
            VisualAsset v = new VisualAsset("v0", "importi-2026.png", "tabella importi",
                    VisualKind.DATA_TABLE, "", "img[src='importi-2026.png']");
            ScreenModel screen = new ScreenModel("s", "", "", 0, 0,
                    List.of(), List.of(), List.of(v), List.of());

            List<Barrier> barriers = service.detectBarriers(screen);
            assertThat(barriers).anyMatch(b -> b.type() == BarrierType.IMAGE_ONLY_DATA);
        }

        @Test
        @DisplayName("UNLABELED_INPUT da FormField con labelProvenance=AI_INFERRED")
        void detectFromInferredField() {
            FormField f = FormField.inferredLabel(
                    "iban", "es. IT60X...", FieldKind.IBAN, null, null, false, List.of());
            ScreenModel screen = new ScreenModel("s", "", "", 0, 0,
                    List.of(), List.of(f), List.of(), List.of());

            List<Barrier> barriers = service.detectBarriers(screen);
            assertThat(barriers).anyMatch(b -> b.type() == BarrierType.UNLABELED_INPUT);
        }

        @Test
        @DisplayName("VISUAL_ONLY_STATE da VisualAsset kind=STEP_INDICATOR")
        void detectFromStepIndicatorVisual() {
            VisualAsset v = new VisualAsset("v0", "step-indicator-4.png", "immagine",
                    VisualKind.STEP_INDICATOR, "", "img[src='step-indicator-4.png']");
            ScreenModel screen = new ScreenModel("s", "", "", 4, 5,
                    List.of(), List.of(), List.of(v), List.of());

            List<Barrier> barriers = service.detectBarriers(screen);
            assertThat(barriers).anyMatch(b -> b.type() == BarrierType.VISUAL_ONLY_STATE);
        }

        @Test
        @DisplayName("VISUAL_ONLY_STATE da regione con prefisso CALENDAR_VISUAL_ONLY:")
        void detectFromCalendarRegion() {
            ScreenRegion r = new ScreenRegion("region-calendar-0", RegionRole.OTHER,
                    null, 0,
                    "CALENDAR_VISUAL_ONLY: 36 div[role=button] con aria-label vuoto",
                    ".calendar-grid");
            ScreenModel screen = new ScreenModel("s", "", "", 0, 0,
                    List.of(r), List.of(), List.of(), List.of());

            List<Barrier> barriers = service.detectBarriers(screen);
            assertThat(barriers).anyMatch(b -> b.type() == BarrierType.VISUAL_ONLY_STATE);
        }

        @Test
        @DisplayName("ERROR_NOT_ANNOUNCED da regione con prefisso ERROR_NO_LIVE:")
        void detectFromErrorRegion() {
            ScreenRegion r = new ScreenRegion("region-errornolive-errore-iban-0",
                    RegionRole.OTHER, null, 0,
                    "ERROR_NO_LIVE: Verificare il codice IBAN inserito.",
                    "#errore-iban");
            ScreenModel screen = new ScreenModel("s", "", "", 0, 0,
                    List.of(r), List.of(), List.of(), List.of());

            List<Barrier> barriers = service.detectBarriers(screen);
            assertThat(barriers).anyMatch(b -> b.type() == BarrierType.ERROR_NOT_ANNOUNCED);
        }

        @Test
        @DisplayName("Nessuna barriera da ScreenModel vuoto")
        void noBarriersFromEmptyScreen() {
            ScreenModel screen = new ScreenModel("s", "", "", 0, 0,
                    List.of(), List.of(), List.of(), List.of());

            List<Barrier> barriers = service.detectBarriers(screen);
            assertThat(barriers).isEmpty();
        }
    }

    // =======================================================================
    // Test sulle pagine demo reali (scenario INPS Assegno Unico)
    // =======================================================================

    @Nested
    @DisplayName("Pagine demo — barriere dello scenario")
    class DemoPageTest {

        /**
         * Barriera b1: IMAGE_ONLY_DATA su index.html.
         * &lt;img src='importi-2026.png' alt='tabella importi'&gt;
         */
        @Test
        @DisplayName("b1 — index.html: IMAGE_ONLY_DATA (importi-2026.png)")
        void b1_imageOnlyData_index() throws IOException {
            ScreenModel screen = perceiveHtml("index.html");
            assertThat(screen.barriers())
                    .as("b1: deve trovare almeno una barriera IMAGE_ONLY_DATA su index.html")
                    .anyMatch(b -> b.type() == BarrierType.IMAGE_ONLY_DATA);
        }

        /**
         * Barriera b2: UNLABELED_INPUT su step4.html.
         * Solo il campo iban non ha label accessibile.
         */
        @Test
        @DisplayName("b2 — step4.html: UNLABELED_INPUT (iban)")
        void b2_unlabeledInput_step4() throws IOException {
            ScreenModel screen = perceiveHtml("step4.html");
            assertThat(screen.barriers())
                    .as("b2: deve trovare almeno una barriera UNLABELED_INPUT su step4.html")
                    .anyMatch(b -> b.type() == BarrierType.UNLABELED_INPUT);
        }

        /**
         * Solo iban e' senza etichetta: il rilevatore non deve essere troppo aggressivo.
         */
        @Test
        @DisplayName("b2 — step4.html: esattamente 1 UNLABELED_INPUT (non di piu')")
        void b2_unlabeledInput_step4_exactlyOne() throws IOException {
            ScreenModel screen = perceiveHtml("step4.html");
            long count = screen.barriers().stream()
                    .filter(b -> b.type() == BarrierType.UNLABELED_INPUT)
                    .count();
            assertThat(count)
                    .as("Su step4.html deve esserci esattamente 1 UNLABELED_INPUT (solo 'iban')")
                    .isEqualTo(1);
        }

        /**
         * Barriera b3: VISUAL_ONLY_STATE su step1.html (step indicator).
         */
        @Test
        @DisplayName("b3 — step1.html: VISUAL_ONLY_STATE (step-indicator-1.png)")
        void b3_visualOnlyState_step1_stepIndicator() throws IOException {
            ScreenModel screen = perceiveHtml("step1.html");
            assertThat(screen.barriers())
                    .as("b3: deve trovare almeno una barriera VISUAL_ONLY_STATE su step1.html")
                    .anyMatch(b -> b.type() == BarrierType.VISUAL_ONLY_STATE);
        }

        /**
         * b3 deve apparire su step4.html (l'indicatore e' presente anche li').
         */
        @Test
        @DisplayName("b3 — step4.html: VISUAL_ONLY_STATE (step-indicator-4.png)")
        void b3_visualOnlyState_step4_stepIndicator() throws IOException {
            ScreenModel screen = perceiveHtml("step4.html");
            assertThat(screen.barriers())
                    .anyMatch(b -> b.type() == BarrierType.VISUAL_ONLY_STATE);
        }

        /**
         * Barriera b4: VISUAL_ONLY_STATE su step1.html (calendario).
         * 36 div[role=button][aria-label=''] per i giorni di marzo 1985.
         */
        @Test
        @DisplayName("b4 — step1.html: VISUAL_ONLY_STATE (calendario 36 div[role=button])")
        void b4_visualOnlyState_step1_calendar() throws IOException {
            ScreenModel screen = perceiveHtml("step1.html");
            // Su step1 devono esserci almeno 2 VISUAL_ONLY_STATE: step indicator + calendario
            long count = screen.barriers().stream()
                    .filter(b -> b.type() == BarrierType.VISUAL_ONLY_STATE)
                    .count();
            assertThat(count)
                    .as("step1.html: deve avere almeno 2 VISUAL_ONLY_STATE (step indicator + calendario)")
                    .isGreaterThanOrEqualTo(2);
        }

        /**
         * Barriera b5: ERROR_NOT_ANNOUNCED su step4.html.
         * div#errore-iban senza aria-live e senza role=alert.
         */
        @Test
        @DisplayName("b5 — step4.html: ERROR_NOT_ANNOUNCED (errore-iban)")
        void b5_errorNotAnnounced_step4() throws IOException {
            ScreenModel screen = perceiveHtml("step4.html");
            assertThat(screen.barriers())
                    .as("b5: deve trovare almeno una barriera ERROR_NOT_ANNOUNCED su step4.html")
                    .anyMatch(b -> b.type() == BarrierType.ERROR_NOT_ANNOUNCED);
        }

        /**
         * index.html non ha step indicator: stepIndex=0, stepCount=0.
         */
        @Test
        @DisplayName("index.html — stepIndex=0, stepCount=0 (non e' un passo)")
        void index_noStepInfo() throws IOException {
            ScreenModel screen = perceiveHtml("index.html");
            assertThat(screen.stepIndex()).isEqualTo(0);
            assertThat(screen.stepCount()).isEqualTo(0);
            assertThat(screen.hasSteps()).isFalse();
        }

        /**
         * step4.html ha step indicator-4.png: stepIndex=4, stepCount=5.
         */
        @Test
        @DisplayName("step4.html — stepIndex=4, stepCount=5")
        void step4_stepInfo() throws IOException {
            ScreenModel screen = perceiveHtml("step4.html");
            assertThat(screen.stepIndex()).isEqualTo(4);
            assertThat(screen.stepCount()).isEqualTo(5);
            assertThat(screen.hasSteps()).isTrue();
        }

        /**
         * step1.html: tutti i campi con label vera (cognome, nome, cf_richiedente)
         * devono avere provenance SOURCE_VERBATIM.
         */
        @Test
        @DisplayName("step1.html — campi etichettati: labelProvenance=SOURCE_VERBATIM")
        void step1_labeledFieldsAreVerbatim() throws IOException {
            ScreenModel screen = perceiveHtml("step1.html");
            List<String> unlabeled = screen.fields().stream()
                    .filter(f -> f.labelProvenance() == Provenance.AI_INFERRED)
                    .map(FormField::id)
                    .toList();
            assertThat(unlabeled)
                    .as("step1.html: nessun campo deve essere AI_INFERRED")
                    .isEmpty();
        }

        /**
         * step2.html: 8 campi, tutti etichettati, nessun UNLABELED_INPUT.
         */
        @Test
        @DisplayName("step2.html — nessun UNLABELED_INPUT (tutti i campi etichettati)")
        void step2_noUnlabeledInput() throws IOException {
            ScreenModel screen = perceiveHtml("step2.html");
            assertThat(screen.barriers())
                    .noneMatch(b -> b.type() == BarrierType.UNLABELED_INPUT);
        }

        /**
         * step3.html: 2 campi, tutti etichettati, nessun UNLABELED_INPUT.
         */
        @Test
        @DisplayName("step3.html — nessun UNLABELED_INPUT")
        void step3_noUnlabeledInput() throws IOException {
            ScreenModel screen = perceiveHtml("step3.html");
            assertThat(screen.barriers())
                    .noneMatch(b -> b.type() == BarrierType.UNLABELED_INPUT);
        }

        /**
         * Il campo iban ha il collegamento alla sua barriera nel modello.
         */
        @Test
        @DisplayName("step4.html — campo iban collegato alla sua barriera UNLABELED_INPUT")
        void step4_ibanLinkedToBarrier() throws IOException {
            ScreenModel screen = perceiveHtml("step4.html");
            FormField iban = screen.field("iban")
                    .orElseThrow(() -> new AssertionError("Campo 'iban' non trovato"));
            assertThat(iban.barrierIds())
                    .as("Il campo 'iban' deve avere almeno un barrierId")
                    .isNotEmpty();
            // Verifica che il barrierId punti a una barriera reale nel modello
            String firstBid = iban.barrierIds().get(0);
            assertThat(screen.barrier(firstBid))
                    .as("Il barrierId '" + firstBid + "' deve corrispondere a una barriera nel modello")
                    .isPresent();
        }

        /**
         * Tutte le 5 barriere dello scenario appaiono percorrendo le pagine principali.
         */
        @Test
        @DisplayName("Scenario completo — tutte le 5 barriere trovate (b1..b5)")
        void allFiveBarriersFound() throws IOException {
            ScreenModel indexScreen = perceiveHtml("index.html");
            ScreenModel step1Screen = perceiveHtml("step1.html");
            ScreenModel step4Screen = perceiveHtml("step4.html");

            // b1
            assertThat(indexScreen.barriers())
                    .as("b1: IMAGE_ONLY_DATA su index.html")
                    .anyMatch(b -> b.type() == BarrierType.IMAGE_ONLY_DATA);

            // b2
            assertThat(step4Screen.barriers())
                    .as("b2: UNLABELED_INPUT su step4.html (iban)")
                    .anyMatch(b -> b.type() == BarrierType.UNLABELED_INPUT);

            // b3 (step indicator presente su step1 e step4)
            assertThat(step1Screen.barriers())
                    .as("b3: VISUAL_ONLY_STATE da step indicator su step1.html")
                    .anyMatch(b -> b.type() == BarrierType.VISUAL_ONLY_STATE);

            // b4 (calendario su step1)
            long step1VisualOnlyCount = step1Screen.barriers().stream()
                    .filter(b -> b.type() == BarrierType.VISUAL_ONLY_STATE).count();
            assertThat(step1VisualOnlyCount)
                    .as("b4: almeno 2 VISUAL_ONLY_STATE su step1 (step indicator + calendario)")
                    .isGreaterThanOrEqualTo(2);

            // b5
            assertThat(step4Screen.barriers())
                    .as("b5: ERROR_NOT_ANNOUNCED su step4.html (errore-iban)")
                    .anyMatch(b -> b.type() == BarrierType.ERROR_NOT_ANNOUNCED);
        }
    }

    // =======================================================================
    // Struttura del ScreenModel
    // =======================================================================

    @Nested
    @DisplayName("Struttura del ScreenModel")
    class ScreenModelStructureTest {

        @Test
        @DisplayName("perceive restituisce un ScreenModel non nullo con screenId")
        void perceiveReturnsNonNull() {
            ScreenModel screen = service.perceive("<html><body><p>ciao</p></body></html>",
                    "http://localhost/test.html");
            assertThat(screen).isNotNull();
            assertThat(screen.screenId()).isNotBlank();
        }

        @Test
        @DisplayName("Il titolo viene estratto dal tag <title>")
        void titleExtracted() {
            ScreenModel screen = service.perceive(
                    "<html><head><title>Pagina di prova</title></head><body></body></html>",
                    "http://localhost/");
            assertThat(screen.title()).isEqualTo("Pagina di prova");
        }

        @Test
        @DisplayName("HTML nullo non lancia eccezione")
        void nullHtmlNoException() {
            assertThat(service.perceive(null, "http://localhost/")).isNotNull();
        }

        @Test
        @DisplayName("URL nullo non lancia eccezione")
        void nullUrlNoException() {
            assertThat(service.perceive("<html><body></body></html>", null)).isNotNull();
        }

        @Test
        @DisplayName("L'input hidden non viene incluso nei campi")
        void hiddenInputExcluded() {
            String html = "<html><body><form>"
                    + "<input type='hidden' id='token' value='abc'>"
                    + "<label for='nome'>Nome</label>"
                    + "<input type='text' id='nome'>"
                    + "</form></body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.field("token")).isEmpty();
            assertThat(screen.field("nome")).isPresent();
        }

        @Test
        @DisplayName("Le immagini vengono estratte come VisualAsset")
        void imagesExtracted() {
            String html = "<html><body>"
                    + "<img src='logo.png' alt='Logo'>"
                    + "<img src='foto.jpg' alt='Una foto'>"
                    + "</body></html>";
            ScreenModel screen = service.perceive(html, "http://localhost/");
            assertThat(screen.visuals()).hasSize(2);
        }
    }
}
