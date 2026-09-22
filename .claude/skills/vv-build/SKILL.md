---
name: vv-build
description: Fase 2 e 3 di VisioVoice - lancia in parallelo i tre builder Java (percezione, narrazione, procedura) piu' l'interfaccia accessibile. Usala dopo /vv-kickoff, quando i contratti condivisi esistono.
---

# Build — quattro agenti in parallelo

## Prerequisito, da verificare e non da assumere

```bash
ls src/main/java/it/visiovoice/model/ && mvn -q test && echo "PRONTO"
```

Se i contratti non esistono o i test sono rossi, **fermati** ed esegui `/vv-kickoff`.
Lanciare i builder su contratti assenti significa che ognuno inventa i tipi che gli servono,
e alla fine ti trovi con tre modelli di dominio incompatibili e nessun tempo per unificarli.

## I quattro agenti, in un unico messaggio

Lanciali **tutti insieme**, in una sola chiamata con quattro invocazioni: così girano
davvero in parallelo invece di accodarsi. Possono farlo perché i loro perimetri di file
sono disgiunti per costruzione:

| Agente | Possiede | Dipende da |
|---|---|---|
| `perception-engineer` | `perception/` | i contratti |
| `narration-engineer` | `narration/`, `llm/`, `fixtures/` | i contratti |
| `procedure-engineer` | `procedure/`, `validation/` | i contratti |
| `a11y-frontend` | `resources/static/` | i contratti API |

In ogni prompt includi **le firme esatte** da `docs/ARCHITECTURE.md`. Non scrivere "implementa
l'agente di percezione": incolla la firma. L'ambiguità qui si paga in divergenza.

Aggiungi a ciascun prompt:

> Leggi `CLAUDE.md` e `docs/PERSONA.md`. Rispetta l'invariante di `docs/PROVENANCE-SPEC.md`.
> Lo scenario è in `src/main/resources/scenarios/`: i dati di dominio si leggono da lì, non
> si scrivono nel codice. Non toccare file fuori dal tuo perimetro. Consegna `mvn -q test`
> verde. Se ti serve un tipo che non esiste, **segnalalo** invece di crearlo.

## Mentre girano

Gli hook lavorano per te: `java-build-gate` sveglia l'agente se rompe la compilazione,
`invariant-guard` blocca i dati di dominio finiti nel codice. Non serve che controlli il
build a mano.

Quello che **devi** fare tu è leggere le richieste di tipi mancanti nei report. Se due
builder chiedono lo stesso campo, è un segnale che i contratti hanno un buco: aggiungilo
tu in `model/` (sei l'orchestratore, quel perimetro è tuo in assenza dell'architetto) e
avvisa entrambi. Non lasciare che se lo aggiungano da soli, o lo faranno in due modi diversi.

## Quando tutti e quattro hanno finito

```bash
mvn -q test && echo "TUTTI I TEST VERDI"
mvn spring-boot:run   # e apri http://localhost:8080
```

Poi `/vv-gate`. **Non salti il gate**: gli agenti che hanno appena scritto il codice sono
gli ultimi da cui accettare un giudizio sulla sua correttezza.

## Se il tempo stringe

Ordine di sacrificio, dal meno al più doloroso:

1. `PdfPerceiver` — la demo usa HTML, il PDF è un extra
2. il calendario accessibile — si sostituisce con un campo data testuale, che per Marco
   è anche **meglio**: digitare `15/03/2026` è più rapido di navigare una griglia
3. il validatore di codice fiscale — l'IBAN basta a dimostrare il punto

Non sacrificare mai, in nessuna circostanza: **il gate di fidelity** e la **navigabilità da
tastiera**. Sono i due requisiti su cui il progetto vive o muore.
