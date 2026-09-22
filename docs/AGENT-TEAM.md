# La squadra di agenti

Mappa di chi fa cosa, chi possiede quali file e in che ordine si lancia.
Definizioni in [`.claude/agents/`](../.claude/agents/), workflow di fase in
[`.claude/skills/`](../.claude/skills/).

## I tre principi di disegno

**1. Un agente = una responsabilità con un proprio "fatto".**
Non un agente per tipo di file. `perception-engineer` esiste perché "la pagina è stata letta
correttamente" è una condizione verificabile in modo indipendente. Un ipotetico
`java-writer` non avrebbe un criterio di completamento proprio, e quindi non saprebbe
quando fermarsi.

**2. Chi costruisce non è chi verifica.**
`fidelity-auditor` non ha `Write` né `Edit`. Non è una limitazione: è la ragione per cui
esiste. Un agente che trova un problema e lo sistema da solo perde l'incentivo a cercare a
fondo, e chi legge il suo report non distingue più fra "il codice era pulito" e "l'ho
ripulito mentre lo controllavo".

**3. Perimetri di file disgiunti, contratti condivisi.**
Tre agenti scrivono Java contemporaneamente senza conflitti perché possiedono package
diversi e dipendono dagli stessi tipi. Il prezzo è che i contratti vanno scritti **prima**,
da un agente solo. Quel prezzo va pagato: è molto più basso di tre refactoring simultanei.

## Chi possiede cosa

| Agente | Perimetro esclusivo | Scrive | Modello |
|---|---|---|---|
| `scenario-researcher` | `resources/scenarios/`, `resources/static/demo/`, `docs/EVIDENCE.md` | sì | sonnet |
| `contract-architect` | `model/`, `agents/`, `llm/`, `orchestrator/`, `api/` | sì | opus |
| `perception-engineer` | `perception/` | sì | sonnet |
| `narration-engineer` | `narration/`, `llm/` (impl), `resources/fixtures/` | sì | sonnet |
| `procedure-engineer` | `procedure/`, `validation/` | sì | sonnet |
| `a11y-frontend` | `resources/static/` | sì | sonnet |
| `fidelity-auditor` | — | **no, per costruzione** | opus |
| `demo-director` | `docs/` | solo docs | sonnet |

`opus` dove serve giudizio strutturale — disegnare i tipi condivisi e valutare se un
controllo è davvero un controllo. `sonnet` dove il compito è definito e conta la velocità.

## Ordine di lancio

```
                    scenario-researcher          (fase 0, da solo)
                             |
                     contract-architect          (fase 1, DA SOLO — nessun parallelismo)
                             |
        +--------------+-----+------+--------------+
        |              |            |              |
  perception     narration    procedure      a11y-frontend   (fase 2/3, IN PARALLELO)
        |              |            |              |
        +--------------+-----+------+--------------+
                             |
                      fidelity-auditor            (fase 4, dopo ogni build)
                             |
                       demo-director              (fase 5, ultima ora)
```

**L'unico vincolo davvero rigido è la fase 1.** Tutto il resto si può riordinare, saltare o
ridurre. Lanciare i builder senza contratti produce tre modelli di dominio incompatibili, e
quello è l'unico errore da cui non si recupera nel tempo disponibile.

## I gate meccanici

Ogni invariante ha un hook che lo impone, così non dipende dal fatto che un agente si ricordi.
Configurazione in [`.claude/settings.json`](../.claude/settings.json).

| Hook | Evento | Cosa fa | Se fallisce |
|---|---|---|---|
| `session-context` | `SessionStart` | inietta persona + stato letto dal filesystem | — |
| `invariant-guard` | `PostToolUse` Edit/Write | dati di dominio nel codice di produzione | **blocca** (exit 2) |
| `invariant-guard` | `PostToolUse` Edit/Write | testo utente senza provenance | avvisa |
| `java-build-gate` | `PostToolUse` Edit/Write | `mvn compile` sui `.java` | **sveglia l'agente** con l'errore del compilatore |
| `agent-activity` | `SubagentStop` | registra quale agente ha finito | — |

Due scelte da spiegare, perché sembrano incoerenti e non lo sono:

- `invariant-guard` **blocca** sui dati di dominio ma solo **avvisa** sulla provenance.
  Il primo controllo è preciso (una stringa con una cifra e "euro" nel codice di produzione
  è sempre un errore); il secondo è euristico e scatta legittimamente sulla definizione del
  record. Un blocco su un'euristica diventa in fretta un hook che qualcuno disattiva — e
  allora non protegge più niente.
- `java-build-gate` gira in `asyncRewake`: non rallenta ogni modifica, ma se il build si
  rompe riporta il modello sul problema. Bloccare per 8 secondi a ogni file, con quattro
  agenti attivi, sarebbe tempo perso in attesa.

## Cosa fa l'orchestratore (il thread principale)

Non scrive codice di dominio. Fa quattro cose:

1. lancia le skill di fase nell'ordine
2. **verifica i criteri di uscita di persona** — `mvn -q test`, non "l'agente ha detto che è fatto"
3. risolve le richieste di tipi mancanti: se due builder chiedono lo stesso campo, i contratti
   hanno un buco e lo colma lui, una volta, per tutti
4. assegna le correzioni dell'audit al proprietario del perimetro giusto

Il punto 2 è quello che salta sempre quando si va di fretta. Un agente che dichiara "fatto"
sta riferendo la propria impressione; `mvn -q test` riferisce un fatto.
