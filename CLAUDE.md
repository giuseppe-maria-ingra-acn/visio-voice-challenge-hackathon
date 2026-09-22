# VisioVoice — costituzione del progetto

> Copilota vocale che **racconta** una schermata a chi non puo' vederla e la **accompagna**
> passo per passo fino in fondo alla procedura.

Questo file e' caricato nel contesto di **ogni** agente. Tienilo corto: i dettagli stanno in
`docs/`, che gli agenti leggono su richiesta.

---

## 1. La persona viene prima del codice

**Marco Ferrari, 41 anni, cieco dalla nascita.** NVDA + display braille. Deve presentare la
domanda di Assegno Unico sul portale INPS. Scheda completa: `docs/PERSONA.md`.

Dove si blocca **oggi**:
1. La tabella "importi per fascia ISEE" e' un'**immagine**. Lo screen reader dice `immagine`.
   Marco non puo' sapere quanto gli spetta.
2. Il form multi-step ha lo step-indicator come immagine, il campo IBAN senza `label`,
   il calendario solo visuale, e gli errori segnalati **solo col colore rosso**.

**Regola d'oro:** prima di scrivere una riga, chiediti *"questo serve a Marco?"*. Se la
risposta e' "fa scena", non scriverlo. Se e' "serve allo sviluppatore",
mettilo in `docs/`, non nel prodotto.

---

## 2. L'invariante di provenance (il cuore del progetto)

Marco **non puo' verificare** cio' che gli diciamo: non vede lo schermo. Quindi:

> **Ogni frase che l'utente ascolta porta con se' la sua origine, e ogni numero pronunciato
> deve essere ritrovabile nella fonte.**

Quattro livelli (`Provenance`): `SOURCE_VERBATIM` < `AI_REPHRASED` < `AI_INFERRED` < `HUMAN_REVIEWED`.

- `AI_REPHRASED` si **guadagna**: il `FidelityAgent` ha ritrovato ogni numero e ogni nome
  proprio nella fonte. Se un solo numero non si ritrova, il segmento **retrocede** a
  `AI_INFERRED` e viene annunciato come "questa parte l'ho dedotta io, conviene verificarla".
- Componendo testo di origini diverse vince **la peggiore** (`Provenance.weakest`).
- Non esiste testo utente senza provenance. Non e' logging: e' un requisito funzionale.

Specifica completa e casi di test: `docs/PROVENANCE-SPEC.md`.
Questo invariante e' cio' che rende **verificabili da una macchina** due requisiti che
altrimenti resterebbero vaghi: semplificare senza tradire, e dichiarare dove ha lavorato l'AI.

---

## 3. I requisiti sono criteri di accettazione

Ognuno ha un **agente responsabile** e un **gate meccanico**. Nessuno puo' dimenticarseli.

| Requisito | Agente responsabile | Gate che lo verifica |
|---|---|---|
| Persona e difficolta' precise | `scenario-researcher` | `docs/PERSONA.md` + `docs/EVIDENCE.md` esistono e sono citati |
| Servizio digitale reale o realistico | `scenario-researcher` | scenario in `resources/scenarios/*.json` con prove |
| Usabile dalla persona stessa | `a11y-frontend` | percorso completo da sola tastiera, zero mouse |
| Semplificare senza tradire | `fidelity-auditor` | `ProvenanceInvariantTest` verde |
| Demo prima/dopo | `demo-director` | `docs/DEMO.md` con i due percorsi |
| Dove ha contribuito l'AI | `fidelity-auditor` | `docs/AI-CONTRIBUTION.md` generato dai trace |

---

## 4. Stack (deciso, non ridiscutere)

- **Java 17** + **Spring Boot 3.3.5**, Maven. Verificato: Maven Central raggiungibile.
- **jsoup** per il parsing HTML. **Nessun Lombok**: si usano `record` e `sealed interface`.
- **Frontend HTML/JS statico** servito da Spring (`resources/static/`). Nessun build step,
  nessun framework: il DOM che scriviamo e' il DOM che lo screen reader legge.
- **LLM astratto** dietro `LlmClient`. `MockLlmClient` (fixtures) e' il default e deve
  sempre funzionare: **la demo non dipende dalla rete**.

Comandi:
```bash
mvn -q compile          # compilazione veloce (lo stesso che gira nell'hook)
mvn -q test             # tutti i test
mvn spring-boot:run     # app su http://localhost:8080
```

---

## 5. Divieti

- **Non inventare dati.** Nessun importo, nessuna scadenza, nessun requisito che non sia
  nello scenario o nella pagina. Se un dato manca: `AI_INFERRED` + richiesta di revisione.
- **Niente React/Streamlit/framework UI.** Contraddicono il requisito centrale del prodotto.
- **Niente `div` con `onclick`.** Elementi nativi (`button`, `label`, `fieldset`) o niente.
- **Non toccare il lavoro di un altro agente.** Ogni agente ha i suoi package (vedi
  `docs/AGENT-TEAM.md`). I contratti condivisi li scrive **solo** `contract-architect`.
- **Non dichiarare fatto cio' che non compila.** `mvn -q test` verde, o non e' fatto.

---

## 6. Come lavora la squadra

Orchestratore = il thread principale. Gli agenti stanno in `.claude/agents/`, i workflow di
fase in `.claude/skills/`.

- **Come si lancia, passo per passo: `docs/GUIDA-OPERATIVA.md`** (comandi e prompt pronti)
- **Come si testa, e cosa non e' automatizzabile: `docs/COME-TESTARE.md`**
- Mappa della squadra, perimetri e dipendenze: `docs/AGENT-TEAM.md`
- Tabella di marcia e budget di tempo: `docs/PLAN.md`

Ordine non negoziabile: **`contract-architect` prima di tutti i builder.** I contratti sono
il vocabolario condiviso; senza di essi gli agenti in parallelo divergono e il merge costa
piu' del tempo che abbiamo.
