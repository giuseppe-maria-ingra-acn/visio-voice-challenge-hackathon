# Tabella di marcia

Sessione di sviluppo di circa 5 ore, due persone. I tempi sono budget, non stime: quando
scade la casella si passa alla successiva con quello che c'è.

## Le fasi

| Ora | Fase | Comando | Chi lavora |
|---|---|---|---|
| 0:00–0:30 | scenario e prove | `/vv-kickoff` (passo 1) | `scenario-researcher` |
| 0:30–1:15 | contratti condivisi | `/vv-kickoff` (passo 2) | `contract-architect`, da solo |
| 1:15–3:00 | build in parallelo | `/vv-build` | 3 builder Java + `a11y-frontend` |
| 3:00–3:30 | primo gate | `/vv-gate` | `fidelity-auditor` |
| 3:30–4:15 | correzioni | — | i proprietari dei perimetri |
| 4:15–4:30 | secondo gate | `/vv-gate` | `fidelity-auditor` |
| 4:30–5:00 | demo | `/vv-demo` | `demo-director` + prova a voce |

## Come si dividono due persone

Il lavoro non è "uno fa il backend e uno il frontend": gli agenti scrivono il codice.
Il lavoro umano è **decidere e verificare**, e si divide così:

**Persona A — orchestrazione.** Lancia le skill, legge i report, colma i buchi nei contratti,
assegna le correzioni. È l'unica che tocca `model/` dopo la fase 1.

**Persona B — verifica reale.** Fa le cose che nessun agente può fare:
- percorre la demo da sola tastiera, col mouse scollegato
- ritrova a mano nella fonte i numeri che il sistema pronuncia (il controllo del controllore)
- legge ad alta voce le frasi principali e giudica il tono
- prova con NVDA, se disponibile

La persona B è quella che protegge il progetto dal rischio più concreto: un sistema che
sembra funzionare perché i test sono verdi, ma che nessuno ha provato come lo proverebbe Marco.

## I due momenti in cui si perde tempo

**Fase 1 troppo lunga.** Perfezionare i contratti è tentante e non finisce mai. Alle 1:15
si passa alla fase 2 con i tipi che ci sono. Un campo mancante si aggiunge in cinque minuti
durante il build; un'ora persa a disegnare non si recupera.

**Correzioni senza proprietario.** Un riscontro dell'audit assegnato "a chi capita" produce
due agenti che modificano lo stesso file. Ogni correzione va al proprietario del perimetro,
sempre, anche quando sembra più rapido farla altrove.

## Cosa si taglia, in quest'ordine

1. `PdfPerceiver` — la demo è su HTML
2. il calendario accessibile — si sostituisce con un campo data testuale, che per Marco è
   anche più rapido da usare
3. il validatore di codice fiscale — l'IBAN basta a dimostrare il punto
4. la sintesi vocale nel browser — NVDA è il canale vero
5. la vista prima/dopo affiancata — si racconta a voce

Mai, in nessuna circostanza: **il gate di fidelity** e la **navigabilità da sola tastiera**.
Senza il primo il prodotto può dire a Marco cose false; senza il secondo lui non può usarlo,
e tutto il resto diventa una dimostrazione per sviluppatori.

## Punti di ritorno

Dopo ogni gate verde:

```bash
git add -A && git commit -m "gate verde: <fase>"
git tag gate-<n>
```

Alle 4:30, prima di toccare qualsiasi cosa per la demo:

```bash
git tag demo-ok
```

Sapere come tornare all'ultima versione funzionante vale più di qualunque miglioria
dell'ultimo quarto d'ora.
