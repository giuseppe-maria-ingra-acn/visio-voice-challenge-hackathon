# Architettura

Stato: **scheletro deciso, firme da completare.** `contract-architect` riempie la sezione
"Firme" in fase 1; i builder la leggono e non la modificano.

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

// i valori arrivano dal DOM letto dall'estensione, non dallo stato di sessione
SpokenScript reviewBeforeSubmit(ProcedurePlan plan, Map<String, String> currentValues);
```

*(elenco iniziale: `contract-architect` lo completa con i record esatti, i costruttori e le
firme dell'API REST)*

## API REST

| Metodo | Percorso | Cosa fa |
|---|---|---|
| `POST` | `/api/session` | apre una sessione su uno scenario |
| `POST` | `/api/perceive` | riceve url/html, restituisce `ScreenModel` + `SpokenScript` |
| `GET` | `/api/narrate/{id}?level=` | narrazione a un livello di dettaglio |
| `POST` | `/api/guide/validate` | l'estensione manda il valore letto dal campo, riceve un giudizio pronunciabile |
| `POST` | `/api/guide/review` | l'estensione manda i valori correnti del form, riceve il riepilogo parlato |
| `GET` | `/api/provenance/{segmentId}` | da dove viene questa frase |
| `GET` | `/api/health` | — |

L'ultimo endpoint della tabella principale (`/api/provenance/...`) alimenta il comando `P`
dell'interfaccia. Non è un extra di debug: è il modo in cui Marco può interrogare quanto
fidarsi di ciò che ha appena sentito.
