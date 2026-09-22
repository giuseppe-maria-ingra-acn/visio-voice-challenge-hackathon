# Architettura

Stato: **scheletro deciso, firme da completare.** `contract-architect` riempie la sezione
"Firme" in fase 1; i builder la leggono e non la modificano.

## Il flusso, in una riga

```
pagina HTML  ->  ScreenModel  ->  SpokenScript  ->  gate di fidelity  ->  voce + tastiera
                      |
                      +------->  ProcedurePlan  ->  coaching campo per campo  ->  invio
```

Due canali che partono dallo stesso modello di schermata: uno **racconta** cosa c'è, l'altro
**accompagna** nella procedura. Sono separati perché rispondono a due domande diverse che
Marco si pone in momenti diversi — *"cosa c'è qui?"* e *"cosa devo fare adesso?"*.

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
└── static/                         a11y-frontend        HTML/CSS/JS, nessun build step
```

## Lo stato di sessione è immutabile

`SessionState` evolve per copia, non per mutazione:

```java
state = state.withPhase(Phase.GUIDE).withAnswer("iban", "IT60...");
```

Costa qualche allocazione in più e restituisce due cose che servono: `AgentTrace` può
conservare gli stati intermedi (ed è da lì che si genera la mappa del contributo AI, invece
di scriverla a memoria), e nessun agente può modificare lo stato che un altro sta leggendo.

## Perché il mock è il default e non un ripiego

`MockLlmClient` è il bean primario. `AnthropicLlmClient` si attiva solo con una variabile
d'ambiente.

Questo rende la demo indipendente dalla rete e i test riproducibili. La rete della sala in
cui gira una demo non è un componente su cui costruire, e un test che chiama un modello
generativo non verifica il codice: verifica il modello.

## Firme

> **Da completare da `contract-architect` in fase 1.** Le firme vanno incollate qui, non
> lasciate nel report del subagente: quel testo si perde alla fine della sessione, e i
> builder paralleli riceverebbero descrizioni a parole invece di contratti — che è
> esattamente il modo in cui tre agenti finiscono con tre vocabolari diversi.

```java
// perception
ScreenModel perceive(String html, String baseUrl);
List<Barrier> detectBarriers(ScreenModel screen);

// narration
SpokenScript narrate(ScreenModel screen, DetailLevel level);
VisualDescription describeVisual(VisualAsset asset, AgentContext ctx);
FidelityReport verify(SpokenScript script, List<SourceFact> facts);

// procedure
ProcedurePlan plan(ScreenModel screen, Scenario scenario);
FieldGuidance coach(FormField field, SessionState state);
ValidationResult validate(FormField field, String userInput);
SpokenScript reviewBeforeSubmit(SessionState state);
```

*(elenco iniziale: `contract-architect` lo completa con i record esatti, i costruttori e le
firme dell'API REST)*

## API REST

| Metodo | Percorso | Cosa fa |
|---|---|---|
| `POST` | `/api/session` | apre una sessione su uno scenario |
| `POST` | `/api/perceive` | riceve url/html, restituisce `ScreenModel` + `SpokenScript` |
| `GET` | `/api/narrate/{id}?level=` | narrazione a un livello di dettaglio |
| `POST` | `/api/guide/answer` | invia il valore di un campo, riceve validazione e passo successivo |
| `GET` | `/api/guide/review` | riepilogo prima dell'invio |
| `GET` | `/api/provenance/{segmentId}` | da dove viene questa frase |
| `GET` | `/api/health` | — |

L'ultimo endpoint della tabella principale (`/api/provenance/...`) alimenta il comando `P`
dell'interfaccia. Non è un extra di debug: è il modo in cui Marco può interrogare quanto
fidarsi di ciò che ha appena sentito.
