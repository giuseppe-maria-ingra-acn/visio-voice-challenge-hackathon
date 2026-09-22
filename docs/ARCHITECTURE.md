# Architettura

Stato: **contratti consegnati (fase 1).** La sezione "Firme" e' normativa: i builder la
leggono e non la modificano. Verificata da `mvn -q test`.

## La forma del prodotto: estensione sottile, backend cervello

VisioVoice è un'**estensione del browser** che lavora *dentro* la pagina che Marco stava già
usando, non un'applicazione separata in cui ridigitare le cose.

```
Marco  ->  pagina del servizio (replica locale)  +  estensione
                                                        |
                                                        |  HTTP  localhost:8080
                                                        v
                                          backend Java: percezione, narrazione,
                                          gate di fidelity, validatori, provenance
                                                        |
                                                        v
                                     l'estensione inietta DOM accessibile
                                                        |
                                                        v
                              NVDA lo legge dall'albero di accessibilità
```

L'estensione fa tre cose e nient'altro: **legge** il DOM, **chiama** il backend, **inietta**
il risultato. Nessuna logica di dominio in JavaScript.

### Perché questa forma e non un'app a fianco

Marco compila **il form del sito**, non una nostra copia. Il protocollo che sente alla fine è
la risposta del sito al suo invio, non un numero che abbiamo generato noi. E VisioVoice non
diventa mai un intermediario che potrebbe sbagliare a ritrasmettere i dati: **la pagina resta
la fonte di verità, l'estensione assiste**.

Conseguenza sul disegno: `SessionState` **non** contiene i valori del form. Traccia la
narrazione e l'avanzamento; i dati stanno nel DOM. Quando serve validare, l'estensione legge
il campo e chiede al backend un giudizio, che annuncia in `aria-live`.

### Su NVDA non c'è nulla da integrare

NVDA non espone un'API a cui agganciarsi: legge l'albero di accessibilità che il browser
pubblica al sistema operativo. Iniettare un `<table>` vero, una `<label for>` e una regione
`aria-live` **è** l'integrazione, ed è l'unica che serve. Funziona anche con JAWS, VoiceOver
e Narrator senza una riga aggiuntiva.

## I due canali

```
DOM della pagina  ->  ScreenModel  ->  SpokenScript  ->  gate di fidelity  ->  DOM accessibile
                           |
                           +------->  ProcedurePlan  ->  coaching campo per campo
```

Uno **racconta** cosa c'è, l'altro **accompagna** nella procedura. Separati perché rispondono
a due domande che Marco si pone in momenti diversi — *"cosa c'è qui?"* e *"cosa devo fare
adesso?"*.

## Gli strati, e quali usano un LLM

| Strato | Cosa fa | LLM | Perché |
|---|---|---|---|
| `perception` | HTML -> `ScreenModel`, rileva barriere | **no** | deterministico, testabile su HTML statico, non può allucinare un campo |
| `narration` | `ScreenModel` -> `SpokenScript` | **sì** | descrivere un'immagine richiede un modello |
| `narration` (gate) | riancora i numeri alla fonte | **no** | un LLM che verifica un LLM condivide i suoi punti ciechi |
| `procedure` | piano dei passi, coaching | parziale | i validatori sono algoritmi normati, il testo è riformulato |
| `validation` | codice fiscale, IBAN, date | **no** | algoritmi con vettori di test noti |
| `orchestrator` | sequenza, stato, trace | no | — |

La colonna "LLM: no" è la parte del sistema di cui possiamo **dimostrare** la correttezza.
Tenerla ampia è una scelta di disegno: più logica sta lì, meno superficie resta da verificare
a mano.

## Package e proprietari

```
it.visiovoice
├── VisioVoiceApplication.java      contract-architect
├── model/                          contract-architect   record di dominio + Provenance
├── agents/                         contract-architect   Agent<I,O>, AgentContext
├── llm/                            contract-architect   LlmClient, MockLlmClient
│                                   narration-engineer   AnthropicLlmClient (se ci sarà una key)
├── orchestrator/                   contract-architect   Orchestrator, SessionState, AgentTrace
├── api/                            contract-architect   controller REST + DTO
├── perception/                     perception-engineer  jsoup, BarrierDetector
├── narration/                      narration-engineer   SpokenScript, FidelityAgent
├── procedure/                      procedure-engineer   ProcedurePlan, FormCoach
└── validation/                     procedure-engineer   CodiceFiscale, Iban, Date

src/main/resources/
├── scenarios/                      scenario-researcher  lo scenario come DATI
├── fixtures/                       narration-engineer   risposte LLM deterministiche
└── static/
    ├── visiovoice.js               a11y-frontend        LA LOGICA, scritta una volta sola
    ├── visiovoice.css              a11y-frontend
    ├── demo/                       scenario-researcher  la replica del servizio, con le
    │                                                    sue barriere e un submit che
    │                                                    risponde con un protocollo
    └── fallback/                   a11y-frontend        pagina minima, rete di sicurezza

extension/                          a11y-frontend        involucro MV3: manifest + loader
                                                          di 5 righe. Nessuna logica qui
```

### Una logica, due veicoli

`visiovoice.js` viene consegnato in due modi, **senza una riga di differenza**:

| Veicolo | Come | Setup | Quando si usa |
|---|---|---|---|
| script incluso | la replica fa `<script src="/visiovoice.js">` | nessuno | percorso della demo |
| estensione MV3 | `loader.js` inserisce quello stesso file nella pagina | caricamento non pacchettizzato | mostra la forma vera del prodotto |

`extension/` sta **fuori** da `src/`: non è codice Java e non entra nel jar. Contiene solo
`manifest.json` e un `loader.js` di cinque righe che inserisce `visiovoice.js` nella pagina.
Nessuna copia duplicata della logica, che andrebbe fuori sincrono nel giro di un'ora.

Conseguenza vincolante: `visiovoice.js` **non può usare le API `chrome.*`**, perché nel
secondo veicolo gira nel contesto della pagina, dove non esistono. Le chiamate all'API sono
same-origin, quindi non servono.

## Lo stato di sessione è immutabile

`SessionState` evolve per copia, non per mutazione:

```java
state = state.withPhase(Phase.GUIDE).withStep(2).withNarrated("seg-14");
```

Costa qualche allocazione in più e restituisce due cose che servono: `AgentTrace` può
conservare gli stati intermedi (ed è da lì che si genera la mappa del contributo AI, invece
di scriverla a memoria), e nessun agente può modificare lo stato che un altro sta leggendo.

Nota cosa **non** c'è in quell'esempio: nessun valore di campo. I dati di Marco stanno nel
DOM della pagina, che è la fonte di verità. `SessionState` traccia dove siamo nel racconto,
non cosa lui ha scritto.

## Perché il mock è il default e non un ripiego

`MockLlmClient` è il bean primario. `AnthropicLlmClient` si attiva solo con una variabile
d'ambiente.

Questo rende la demo indipendente dalla rete e i test riproducibili. La rete della sala in
cui gira una demo non è un componente su cui costruire, e un test che chiama un modello
generativo non verifica il codice: verifica il modello.

## Firme

> **Scritte da `contract-architect` in fase 1, verificate da `mvn -q test` (36 test verdi).**
> I builder le leggono e **non le modificano**. Se manca un campo, si chiede
> all'orchestratore: lo aggiunge lui una volta per tutti, invece di quattro volte in
> quattro modi.
>
> Convenzioni valide per tutti i record qui sotto: `Provenance` e le altre `Objects.requireNonNull`
> indicate **lanciano** se nulle (sono bug di programmazione, non errori dell'utente); ogni
> `List`/`Map` accetta `null` e diventa una lista vuota immutabile; nessun setter esiste, si
> copia con i metodi `withX`.

### Provenance, e il gate che la fa rispettare

```java
package it.visiovoice.model;

public enum Provenance {
    SOURCE_VERBATIM, AI_REPHRASED, AI_INFERRED, HUMAN_REVIEWED;

    public String italianLabel();            // etichetta pronunciabile
    public boolean needsHumanReview();       // true solo per AI_INFERRED
    public int rank();                       // 0 = HUMAN_REVIEWED ... 3 = AI_INFERRED
    public String spokenDisclaimer();        // "Attenzione: questa parte l'ho dedotta io..." o ""
    public static Provenance weakest(Provenance a, Provenance b);
}
```

`spokenDisclaimer()` sta nell'enum e non nei singoli agenti: quattro avvertenze scritte con
quattro parole diverse insegnano all'utente che l'avvertenza non significa niente.

```java
package it.visiovoice.model;

public final class ProvenanceGate {                 // static, senza stato, senza LLM

    public static final String SEMANTIC_LIMIT_NOTE; // il limite dichiarato del controllo

    public static String normalizeNumber(String raw);
    public static String normalizeText(String text);
    public static List<String> extractNumbers(String text);
    public static List<String> extractProperNouns(String text);

    public static AnchorCheck check(ScriptSegment segment, String corpus);
    public static ScriptSegment enforce(ScriptSegment segment, String corpus);
    public static String corpusFor(ScriptSegment segment, List<SourceFact> facts, String extraCorpus);

    public static FidelityVerdict verify(SpokenScript script, List<SourceFact> facts, String extraCorpus);
    public static FidelityVerdict verify(SpokenScript script, List<SourceFact> facts);
}

public record AnchorCheck(boolean anchored, Provenance effective, List<UnanchoredToken> unanchored) {}
public record FidelityVerdict(SpokenScript script, FidelityReport report) { public boolean isClean(); }
```

**Il gate sta in `model/`, non in `narration/`.** È la regola normativa del prodotto: chi narra
la **usa**, nessuno la riscrive. `narration.FidelityAgent` deve chiamare
`ProvenanceGate.verify(...)` e restituire `verdict.script()`, non lo script originale — il
verdetto contiene i segmenti **già retrocessi**.

Solo i segmenti `AI_REPHRASED` vengono esaminati: `SOURCE_VERBATIM` è copiato, `AI_INFERRED` è
già al livello più debole, `HUMAN_REVIEWED` è una firma umana che una regex non ha titolo a
revocare.

### Il tipo centrale: `ScriptSegment`

```java
package it.visiovoice.model;

public record ScriptSegment(
        String id,                  // requireNonNull - chiave di GET /api/provenance/{id}
        SegmentRole role,           // requireNonNull
        String text,                // requireNonNull - italiano, pronunciabile
        Provenance provenance,      // requireNonNull - IMPOSSIBILE da dimenticare
        List<String> sourceRefs) {  // id dei SourceFact o selettori DOM

    public static ScriptSegment verbatim(String id, SegmentRole role, String text, List<String> sourceRefs);
    public static ScriptSegment rephrased(String id, SegmentRole role, String text, List<String> sourceRefs);
    public static ScriptSegment inferred(String id, SegmentRole role, String text, List<String> sourceRefs);

    public ScriptSegment withProvenance(Provenance newProvenance);
    public ScriptSegment demoted();      // -> AI_INFERRED
    public boolean needsHumanReview();
    public String spokenText();          // avvertenza + testo, nell'ordine in cui si pronuncia
}

public enum SegmentRole {
    TITLE, OVERVIEW, STEP_STATUS, DATA, BARRIER_REPAIR, FIELD_PROMPT,
    NAVIGATION, WARNING, ERROR, REVIEW, CONFIRMATION, HELP
}
```

`ScriptSegment.rephrased(...)` è una **candidatura**, non una promozione: il livello resta
`AI_REPHRASED` solo se il gate ritrova ogni numero e ogni nome proprio.

```java
public record SpokenScript(String id, String screenId, DetailLevel level, List<ScriptSegment> segments) {
    public static SpokenScript of(String id, String screenId, DetailLevel level, List<ScriptSegment> segments);
    public SpokenScript withSegments(List<ScriptSegment> newSegments);
    public Optional<ScriptSegment> segment(String segmentId);
    public List<ScriptSegment> byRole(SegmentRole role);
    public Provenance weakestProvenance();
    public boolean needsHumanReview();
    public String plainText();
}

public enum DetailLevel { BRIEF, STANDARD, FULL;  public String italianLabel(); }
```

### Cosa c'è sulla schermata (prodotto da `perception`)

```java
public record ScreenModel(
        String screenId,            // requireNonNull
        String url,
        String title,
        int stepIndex,              // 0 = non determinabile
        int stepCount,              // 0 = non e' una procedura a passi
        List<ScreenRegion> regions,
        List<FormField> fields,
        List<VisualAsset> visuals,
        List<Barrier> barriers) {

    public Optional<FormField> field(String fieldId);
    public Optional<Barrier> barrier(String barrierId);
    public List<Barrier> barriersFor(String fieldId);
    public boolean hasSteps();
    public String textCorpus();      // corpus di ancoraggio lato DOM
}

public record ScreenRegion(String id, RegionRole role, String heading, int headingLevel,
                           String text, String domSelector) {
    public static ScreenRegion of(String id, RegionRole role, String text);
}

public enum RegionRole { HEADER, NAV, HEADING, PARAGRAPH, INFO_BOX, LIST, TABLE, FORM,
                         FIELDSET, IMAGE, ACTION, FOOTER, OTHER }
```

### Campi, barriere, immagini

```java
public record FormField(
        String id,                      // requireNonNull
        String label,                   // puo' essere null: e' il caso del campo senza label
        Provenance labelProvenance,     // requireNonNull - SOURCE_VERBATIM se letta nel DOM,
                                        //                  AI_INFERRED se gliela diamo noi
        FieldKind kind,                 // null -> OTHER
        String format,
        String example,
        boolean required,
        List<String> barrierIds,        // LISTA: un campo puo' avere piu' barriere insieme
        String domSelector) {           // null -> "#" + id

    public static FormField labelled(String id, String label, FieldKind kind, String format,
                                     String example, boolean required);
    public static FormField inferredLabel(String id, String label, FieldKind kind, String format,
                                          String example, boolean required, List<String> barrierIds);
    public boolean hasInferredLabel();
    public FormField withBarrierIds(List<String> ids);
}

public enum FieldKind { TEXT, FISCAL_CODE, IBAN, DATE, CURRENCY, NUMBER, EMAIL, PHONE,
                        SELECT, CHECKBOX, RADIO, OTHER }
```

```java
public record Barrier(
        String id,                      // requireNonNull
        BarrierType type,               // null -> UNKNOWN
        String location,
        String screenReaderHears,       // null -> "" ; vuoto = barriera silenziosa
        String whyBlocking,
        String source,                  // la citazione WCAG / il monitoraggio
        EvidenceFidelity fidelity) {    // OSSERVATO o RICOSTRUITO, per singola barriera

    public static Barrier detected(String id, BarrierType type, String location,
                                   String screenReaderHears, String whyBlocking);  // OSSERVATO
    public boolean isSilent();
}

public enum BarrierType { IMAGE_ONLY_DATA, UNLABELED_INPUT, VISUAL_ONLY_STATE,
                          ERROR_NOT_ANNOUNCED, KEYBOARD_INACCESSIBLE, MISSING_STRUCTURE,
                          ILLOGICAL_ORDER, UNKNOWN;   public String italianLabel(); }

public enum EvidenceFidelity { OSSERVATO, RICOSTRUITO;  public String italianLabel(); }
public enum ServiceFidelity  { LIVE, REPLICA, MOCKUP }
```

```java
public record VisualAsset(String id, String src, String altText, VisualKind kind,
                          String nearbyText, String domSelector) {
    public boolean altIsUseless();   // alt vuoto o generico ("immagine", "grafico", ...)
}

public enum VisualKind { DATA_TABLE, STEP_INDICATOR, CHART, ICON, PHOTO, DECORATIVE, UNKNOWN }

public record VisualDescription(
        String assetId,                 // requireNonNull
        String summary,                 // requireNonNull
        List<AccessibleTable> tables,
        Provenance provenance,          // requireNonNull
        List<String> sourceRefs) {
    public boolean hasTable();
}

public record AccessibleTable(String caption, List<String> headers, List<List<String>> rows,
                              Provenance provenance, List<String> sourceRefs) {
    public String cellText();        // tutte le celle di seguito: il testo che il gate ancora
}
```

Una tabella raccontata a parole si può ascoltare ma non **consultare**: `AccessibleTable`
esiste perché il PNG degli importi diventi un `<table>` vero con `<th scope>`, navigabile per
righe e colonne. Riassumerla a voce soddisferebbe il requisito formale lasciando in piedi il
problema.

### Lo scenario come dati

```java
public record Scenario(
        String id,                          // requireNonNull
        ServiceInfo service,
        String persona,
        String goal,
        List<Barrier> barriers,
        List<SourceFact> sourceFacts,       // il corpus del gate
        List<ComputedFact> computedFacts,   // NON entrano nel corpus
        List<ScenarioStep> procedure) {

    public Optional<Barrier> barrier(String barrierId);
    public Optional<SourceFact> sourceFact(String factId);
    public Optional<ComputedFact> computedFact(String factId);
    public Optional<ScenarioStep> step(int index);
    public List<Barrier> barriersOfType(BarrierType type);
    public String sourceCorpus();           // solo sourceFacts + titoli/campi della procedura
}

public record ServiceInfo(String name, String authority, String url, ServiceFidelity fidelity) {}

public record SourceFact(String id, String text, String ref, Provenance provenance) {
    // provenance null -> SOURCE_VERBATIM (un fatto citato da una fonte, per definizione)
    public static SourceFact of(String id, String text, String ref);
}

public record ComputedFact(String id, String text, String formula,
                           List<String> derivedFrom, String ref) {
    public Provenance provenance();   // SEMPRE AI_INFERRED: non e' un campo, non si sovrascrive
}
```

`ComputedFact` è separato da `SourceFact` perché un valore calcolato è `AI_INFERRED` **per
costruzione**, non perché il gate non l'ha trovato. Se i calcolati entrassero in
`sourceCorpus()`, un importo moltiplicato ancorerebbe se stesso e il gate direbbe "verificato
sulla fonte" di un numero che nessuna fonte contiene.

```java
public record ScenarioStep(int step, String title, List<String> requiredData,
                           List<ScenarioField> fields, String note, ScenarioOutput output,
                           String completionCriterion) {
    public Optional<ScenarioField> field(String fieldId);
    public List<String> barrierIds();
}

public record ScenarioField(String id, String label, String format, String example,
                            boolean required, String barrier, List<String> barriers,
                            String accessibleNote) {
    public List<String> barrierIds();                        // unisce "barrier" e "barriers"
    public FormField asFormField(Provenance labelProvenance); // provenance OBBLIGATORIA
    public FieldKind guessKind();                             // euristica su id + format
}

public record ScenarioOutput(String id, String label, String format, String example, String note) {}
```

`ScenarioField` non è un `FormField`: nel `FormField` la provenance dell'etichetta è
obbligatoria, e uno scenario JSON non può dichiararla per quindici campi senza diventare
illeggibile. Passare da `asFormField(Provenance)` costringe chi converte a dire da dove viene
quell'etichetta — che per l'IBAN, che sulla pagina non ha `<label>`, è l'unica domanda che conta.

**Usare sempre `barrierIds()`, mai i campi `barrier` / `barriers` direttamente.**

### Procedura, guida, validazione

```java
public record ProcedurePlan(String scenarioId, String goal, List<ProcedureStep> steps) {
    public Optional<ProcedureStep> step(int index);
    public int totalSteps();
    public List<FormField> allFields();
    public Optional<FormField> field(String fieldId);
}

public record ProcedureStep(
        int index,
        String title,                   // requireNonNull
        List<String> requiredData,
        List<FormField> fields,
        String completionCriterion,     // requireNonNull - "vai avanti quando hai finito" non basta
        Provenance provenance) {        // requireNonNull
    public List<FormField> requiredFields();
    public boolean isSatisfiedBy(Map<String, String> currentValues);   // i valori vengono da fuori
}

public record FieldGuidance(
        String fieldId,                 // requireNonNull
        String spokenLabel,             // requireNonNull
        String instruction,             // null -> ""
        String example,
        Provenance provenance,          // requireNonNull
        List<String> sourceRefs,
        List<String> barrierIds) {
    public String spokenText();                        // avvertenza + nome + istruzione + esempio
    public ScriptSegment asSegment(String segmentId);  // ruolo FIELD_PROMPT
}

public record ValidationResult(
        String fieldId,
        boolean valid,
        Severity severity,              // null -> OK se valid, ERROR altrimenti
        String spokenMessage,           // requireNonNull - MAI vuoto, nemmeno in caso di successo
        Provenance provenance,          // requireNonNull
        String normalizedValue) {

    public static ValidationResult valid(String fieldId, String spokenMessage, String normalizedValue);
    public static ValidationResult invalid(String spokenMessage);
    public static ValidationResult invalid(String fieldId, String spokenMessage);
    public static ValidationResult warning(String fieldId, String spokenMessage, String normalizedValue);
    public ScriptSegment asSegment(String segmentId);
}

public enum Severity { OK, WARNING, ERROR }
```

I messaggi dei validatori sono `HUMAN_REVIEWED`: sono costanti scritte e riviste da una
persona, non testo generato a runtime. Marcarli come dedotti riempirebbe la procedura di
avvertenze, e un'avvertenza che compare sempre è un'avvertenza che nessuno ascolta più.

`spokenMessage` non è mai vuoto **anche quando il valore è corretto**: il silenzio dopo un
controllo è indistinguibile dal controllo che non è partito, ed è esattamente la barriera
"più crudele" dello scenario.

### Rapporto di fidelity

```java
public record FidelityReport(
        String scriptId,
        int segmentsChecked,                    // zero problemi su zero segmenti non e' pulito
        List<String> demotedSegmentIds,
        List<UnanchoredToken> unanchored,
        List<String> reviewNeeded) {            // contiene sempre SEMANTIC_LIMIT_NOTE

    public static FidelityReport clean(String scriptId, int segmentsChecked);
    public boolean isClean();
    public boolean hasFindings();
    public String spokenSummary();
}

public record UnanchoredToken(String segmentId, TokenKind kind, String token, String normalized) {
    public String describe();
}

public enum TokenKind { NUMBER, PROPER_NOUN }
```

### Le quattro porte da implementare

**Queste sono le firme che i builder implementano.** Un `@Service` che implementa la porta
viene raccolto automaticamente dai controller: non serve registrarlo da nessuna parte.

```java
package it.visiovoice.agents;

// perception-engineer
public interface PerceptionPort {
    ScreenModel perceive(String html, String baseUrl);
    List<Barrier> detectBarriers(ScreenModel screen);
}

// narration-engineer
public interface NarrationPort {
    SpokenScript narrate(ScreenModel screen, DetailLevel level, AgentContext ctx);
    VisualDescription describeVisual(VisualAsset asset, AgentContext ctx);
}

// procedure-engineer
public interface ProcedurePort {
    ProcedurePlan plan(ScreenModel screen, Scenario scenario);
    FieldGuidance coach(FormField field, SessionState state);
    SpokenScript reviewBeforeSubmit(ProcedurePlan plan, Map<String, String> currentValues);
}

// procedure-engineer (package validation/)
public interface ValidationPort {
    ValidationResult validate(FormField field, String userInput);
    boolean supports(FormField field);
}
```

`narrate(...)` deve restituire uno script **già passato dal gate**:
`return ProvenanceGate.verify(script, ctx.scenario().sourceFacts(), screen.textCorpus()).script();`

`reviewBeforeSubmit` riceve i valori come parametro perché sono stati letti dal DOM in quell
istante. La pagina è la fonte di verità: un riepilogo costruito su una nostra copia potrebbe
descrivere un form diverso da quello che l'utente sta per inviare.

### Agente e contesto

```java
package it.visiovoice.agents;

public interface Agent<I, O> {
    String name();
    O run(I input, AgentContext ctx);
}

public record AgentContext(String sessionId, Scenario scenario, DetailLevel level,
                           Map<String, String> options) {
    public static AgentContext of(String sessionId, Scenario scenario, DetailLevel level);
    public Optional<String> option(String key);
    public AgentContext withOption(String key, String value);
    public AgentContext withLevel(DetailLevel newLevel);
}
```

### LLM: astratto, mock primario

```java
package it.visiovoice.llm;

public interface LlmClient {
    String name();
    boolean available();
    LlmResponse complete(LlmRequest request);   // mai null: al peggio una risposta vuota
}

public record LlmRequest(String promptId,       // requireNonNull - E' IL NOME DELLA FIXTURE
                         String system,
                         String user,           // requireNonNull
                         Map<String, String> variables,
                         int maxTokens) {       // <= 0 -> 1024
    public static LlmRequest of(String promptId, String user);
    public LlmRequest withVariable(String key, String value);
}

public record LlmResponse(String text,          // requireNonNull
                          String model,
                          boolean fromFixture,
                          Provenance claimedProvenance,  // una PRETESA, non un verdetto
                          List<String> sourceRefs,
                          long latencyMs) {
    public static LlmResponse fixture(String text, String model, List<String> sourceRefs, long latencyMs);
    public boolean isEmpty();
}

@Component @Primary
public class MockLlmClient implements LlmClient {
    public static final String FIXTURE_DIR = "fixtures/";
    public boolean hasFixture(String promptId);
}
```

Fixture risolte per `promptId` in `resources/fixtures/{promptId}.{txt,json,md}`, in
quest'ordine. Segnaposto `{{nome}}` sostituiti dalle `variables`. Se la fixture manca, il
client **non inventa**: risponde dichiarandolo, con `claimedProvenance = AI_INFERRED`.
Fixture già presenti: `selftest.txt`, `descrivi-tabella-importi.json`,
`descrivi-indicatore-passi.txt`. Le altre le scrive `narration-engineer`; formato in
`resources/fixtures/LEGGIMI.md`.

### Stato di sessione e orchestrazione

```java
package it.visiovoice.orchestrator;

public record SessionState(String sessionId, String scenarioId, Phase phase, int currentStep,
                           String currentScreenId, DetailLevel level,
                           List<String> narratedSegmentIds, List<AgentTrace> traces,
                           Instant openedAt) {

    public static SessionState open(String sessionId, String scenarioId, DetailLevel level, Instant now);
    public SessionState withPhase(Phase newPhase);
    public SessionState withStep(int step);
    public SessionState withScreen(String screenId);
    public SessionState withLevel(DetailLevel newLevel);
    public SessionState withNarrated(String segmentId);
    public SessionState withTrace(AgentTrace trace);
    public Optional<String> lastNarratedSegmentId();
    public boolean hasNarrated(String segmentId);
}

public enum Phase { INTRO, NARRATE, GUIDE, REVIEW, DONE }

public record AgentTrace(String id, String agentName, String sessionId, Instant startedAt,
                         long durationMs, String inputSummary, String outputSummary,
                         Provenance outputProvenance, boolean ok, String note) {
    public static AgentTrace success(String id, String agentName, String sessionId, Instant startedAt,
                                     long durationMs, String inputSummary, String outputSummary,
                                     Provenance outputProvenance);
    public static AgentTrace failure(String id, String agentName, String sessionId, Instant startedAt,
                                     long durationMs, String inputSummary, String note);
    public String describe();
}
```

**`SessionState` non contiene nessun valore di campo**, e un test lo verifica per riflessione
(`SessionStateTest.nessunValoreDiCampoNelloStato`). Se ne tenessimo una copia, VisioVoice
diventerebbe un intermediario che può ritrasmettere un dato diverso da quello che l'utente ha
scritto — e lui, che non vede lo schermo, non avrebbe modo di accorgersene.

```java
@Component public class SessionStore {
    public SessionState open(String scenarioId, DetailLevel level);
    public Optional<SessionState> find(String sessionId);
    public SessionState save(SessionState state);
    public Optional<SessionState> update(String sessionId, UnaryOperator<SessionState> change);  // atomico
    public int count();
}

@Component public class ScenarioStore {
    public Optional<Scenario> find(String scenarioId);
    public List<Scenario> all();
    public Optional<Scenario> defaultScenario();
    public List<String> loadErrors();
    public int count();
}

@Component public class ScriptStore {           // alimenta GET /api/provenance/{segmentId}
    public SpokenScript remember(SpokenScript script);      // da chiamare DOPO il gate
    public void rememberReport(FidelityReport report);
    public Optional<SpokenScript> script(String scriptId);
    public Optional<ScriptSegment> segment(String segmentId);
    public Optional<FidelityReport> report(String scriptId);
    public int segmentCount();
}

@Service public class Orchestrator {
    public <I, O> O run(Agent<I, O> agent, I input, AgentContext ctx, Function<O, Provenance> provenanceOf);
    public <I, O> O run(Agent<I, O> agent, I input, AgentContext ctx);
}

@Configuration public class ClockConfiguration { @Bean public Clock clock(); }
```

### Firme dei controller

```java
package it.visiovoice.api;

@RestController @RequestMapping("/api")
class HealthController {
    @GetMapping("/health")  HealthResponse health();
}

@RestController @RequestMapping("/api/session")
class SessionController {
    @PostMapping            ResponseEntity<Object> open(@RequestBody(required=false) SessionRequest r);
    @GetMapping("/{sessionId}")  ResponseEntity<Object> read(@PathVariable String sessionId);
}

@RestController @RequestMapping("/api")
class PerceiveController {
    @PostMapping("/perceive")          ResponseEntity<Object> perceive(@RequestBody PerceiveRequest r);
    @GetMapping("/narrate/{scriptId}") ResponseEntity<Object> narrate(@PathVariable String scriptId,
                                                 @RequestParam(required=false) DetailLevel level);
}

@RestController @RequestMapping("/api/guide")
class GuideController {
    @PostMapping("/validate")  ResponseEntity<Object> validate(@RequestBody ValidateRequest r);
    @PostMapping("/review")    ResponseEntity<Object> review(@RequestBody ReviewRequest r);
}

@RestController @RequestMapping("/api/provenance")
class ProvenanceController {
    @GetMapping("/{segmentId}")  ResponseEntity<Object> describe(@PathVariable String segmentId);
}
```

I controller ricevono le porte come `ObjectProvider<...>`, non come dipendenza obbligatoria:
l'applicazione **si avvia e risponde anche prima che i quattro strati esistano**, e quando una
porta manca risponde `503` con un messaggio pronunciabile invece di non partire. Un contesto
Spring che non parte per un bean mancante blocca tutti e quattro gli agenti insieme.

### DTO dell'API

```java
package it.visiovoice.api.dto;

public record HealthResponse(String status, String version, String llm, boolean llmFromFixtures,
                             int scenarios, List<String> scenarioIds,
                             List<String> missingAgents, List<String> scenarioLoadErrors) {}

public record SessionRequest(String scenarioId, DetailLevel level) {}
public record SessionResponse(String sessionId, String scenarioId, String goal, Phase phase,
                              int currentStep, int totalSteps, DetailLevel level) {}

public record PerceiveRequest(String sessionId, String url, String html, DetailLevel level) {}
public record PerceiveResponse(String sessionId, ScreenModel screen, SpokenScript script,
                               FidelityReport fidelity) {}

public record ValidateRequest(String sessionId, String fieldId, String value) {}   // un campo alla volta
public record ReviewRequest(String sessionId, Map<String, String> values) {}       // letti dal DOM

public record ProvenanceResponse(String segmentId, String text, Provenance provenance,
                                 String italianLabel, boolean needsHumanReview,
                                 List<String> sourceRefs, String spokenExplanation) {}

public record ErrorResponse(String code, String spokenMessage) {
    public static ErrorResponse of(String code, String spokenMessage);
}
```

`POST /api/guide/validate` risponde con un `ValidationResult` serializzato direttamente: ha già
un messaggio pronunciabile, e un DTO in mezzo aggiungerebbe solo un punto in cui il testo si
può perdere. `POST /api/guide/review` risponde con uno `SpokenScript`.

Gli errori escono come `ErrorResponse`, mai come stacktrace: un'eccezione che arriva
all'interfaccia diventa una frase che una sintesi vocale legge all'utente.

## Tipo -> proprietario

| Tipo / file | Package | Proprietario | Chi lo consuma |
|---|---|---|---|
| `Provenance`, `ProvenanceGate`, `AnchorCheck` | `model` | `contract-architect` | tutti |
| `ScriptSegment`, `SpokenScript`, `SegmentRole`, `DetailLevel` | `model` | `contract-architect` | narration, procedure, a11y |
| `ScreenModel`, `ScreenRegion`, `RegionRole` | `model` | `contract-architect` | perception (produce), narration (consuma) |
| `FormField`, `FieldKind` | `model` | `contract-architect` | perception, procedure, validation |
| `Barrier`, `BarrierType`, `EvidenceFidelity` | `model` | `contract-architect` | perception (produce), narration (racconta) |
| `VisualAsset`, `VisualDescription`, `AccessibleTable`, `VisualKind` | `model` | `contract-architect` | perception, narration, a11y |
| `ProcedurePlan`, `ProcedureStep`, `FieldGuidance`, `ValidationResult`, `Severity` | `model` | `contract-architect` | procedure, validation |
| `FidelityReport`, `FidelityVerdict`, `UnanchoredToken`, `TokenKind` | `model` | `contract-architect` | narration, fidelity-auditor |
| `Scenario`, `ServiceInfo`, `SourceFact`, `ComputedFact`, `ScenarioStep`, `ScenarioField`, `ScenarioOutput`, `ServiceFidelity` | `model` | `contract-architect` | tutti (lettura); **i dati** li scrive `scenario-researcher` |
| `Agent`, `AgentContext`, le quattro `*Port` | `agents` | `contract-architect` | i quattro builder le implementano |
| `LlmClient`, `LlmRequest`, `LlmResponse`, `MockLlmClient` | `llm` | `contract-architect` | narration |
| `AnthropicLlmClient` (eventuale) | `llm` | `narration-engineer` | — |
| `SessionState`, `Phase`, `AgentTrace`, `SessionStore`, `ScenarioStore`, `ScriptStore`, `Orchestrator` | `orchestrator` | `contract-architect` | api, procedure |
| controller + `dto/` | `api` | `contract-architect` | `visiovoice.js` |
| `resources/fixtures/` | — | `narration-engineer` | `MockLlmClient` |
| `resources/scenarios/` | — | `scenario-researcher` | `ScenarioStore` |

**Nessun builder modifica `model/`, `agents/`, `llm/`, `orchestrator/`, `api/`.** Se manca un
campo si chiede all'orchestratore: lo aggiunge una volta, per tutti. Quattro aggiunte
parallele allo stesso record sono quattro conflitti alla terza ora.
## API REST

| Metodo | Percorso | Cosa fa |
|---|---|---|
| `POST` | `/api/session` | apre una sessione su uno scenario |
| `POST` | `/api/perceive` | riceve url/html, restituisce `ScreenModel` + `SpokenScript` |
| `GET` | `/api/narrate/{scriptId}?level=` | rilegge un racconto gia' prodotto, col suo rapporto di fidelity |
| `POST` | `/api/guide/validate` | `{sessionId, fieldId, value}` -> `ValidationResult` con messaggio pronunciabile |
| `POST` | `/api/guide/review` | `{sessionId, values}` letti dal DOM -> `SpokenScript` di riepilogo |
| `GET` | `/api/provenance/{segmentId}` | da dove viene questa frase |
| `GET` | `/api/session/{sessionId}` | rilegge lo stato di una sessione |
| `GET` | `/api/health` | stato, client dei modelli attivo, scenari caricati, strati non ancora collegati |

L'ultimo endpoint della tabella principale (`/api/provenance/...`) alimenta il comando `P`
dell'interfaccia. Non è un extra di debug: è il modo in cui Marco può interrogare quanto
fidarsi di ciò che ha appena sentito.
