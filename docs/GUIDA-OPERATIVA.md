# Guida operativa — dal repo vuoto alla demo

Tutti i passi, in ordine, con i comandi da copiare. Se qualcosa non torna, la sezione
[Quando qualcosa non va](#quando-qualcosa-non-va) è in fondo.

**Come leggere questa guida:** i blocchi ```bash``` si incollano nel terminale. I blocchi
citati con `>` si incollano nel prompt di Claude Code.

---

## Passo 0 — preparazione, una volta sola

### 0.1 Verifica la toolchain

```bash
java -version      # atteso: 17.x
mvn -version       # atteso: 3.9.x
python --version   # atteso: 3.x  (serve agli hook)
```

Se `java` non è la 17, gli agenti scriveranno codice che non compila per motivi che non
capirete subito. Sistemate questo prima di tutto il resto.

### 0.2 Attiva gli hook — IL PASSO CHE SI DIMENTICA

Gli hook di questo progetto **non sono attivi finché non fate una di queste due cose**:

- digitate **`/hooks`** una volta (ricarica la configurazione), **oppure**
- **riavviate Claude Code**

Motivo tecnico: Claude Code sorveglia `.claude/settings.json` solo nelle cartelle che
avevano già un file di impostazioni all'avvio della sessione. Se quel file è stato creato
*durante* la sessione, il sorvegliante non c'è e gli hook restano inerti. Skill e agenti
vengono invece scoperti da una scansione separata, ed è per questo che compaiono subito:
è la ragione per cui questo problema inganna — sembra che tutto sia caricato.

**Verifica che siano davvero attivi.** Non fidatevi, provate:

```bash
mkdir -p src/main/java/it/visiovoice
```

Poi chiedete a Claude Code:

> Scrivi il file `src/main/java/it/visiovoice/Probe.java` con una costante di tipo String
> che contiene il testo "ti spettano 149,00 euro al mese".

- **Se gli hook sono attivi:** la scrittura viene **bloccata** con il messaggio
  `INVARIANTE VIOLATO - dato di dominio dentro il codice di produzione`.
- **Se non lo sono:** il file viene creato senza obiezioni. Tornate a `/hooks`.

Poi ripulite:

```bash
rm -rf src
```

### 0.3 Controlla che la squadra sia caricata

Nel prompt di Claude Code digitate `/` e cercate `vv-kickoff`, `vv-build`, `vv-gate`,
`vv-demo`. Se non ci sono, riavviate.

---

## Come si lancia un agente

Tre modi, dal più al meno controllato.

**1. Con la skill di fase (consigliato).** La skill sa quali agenti servono, in che ordine,
e cosa verificare in mezzo:

```
/vv-kickoff
```

**2. Nominando l'agente.** Utile per una correzione puntuale:

> Usa l'agente `perception-engineer` per aggiungere il rilevamento delle barriere di tipo
> `KEYBOARD_TRAP`. Leggi prima `docs/PERSONA.md`.

**3. Più agenti in parallelo.** Chiedete esplicitamente il parallelismo, altrimenti si
accodano:

> Lancia **in parallelo, in un solo messaggio**, gli agenti `perception-engineer`,
> `narration-engineer`, `procedure-engineer` e `a11y-frontend`, ognuno sul proprio perimetro.

Il parallelismo funziona perché i perimetri di file sono disgiunti
([`docs/AGENT-TEAM.md`](AGENT-TEAM.md)). Non lanciate in parallelo due agenti che
scrivono lo stesso package: si sovrascrivono a vicenda.

---

## Passo 1 — lo scenario e le sue prove (~30 min)

```
/vv-kickoff
```

La skill esegue `scenario-researcher`. Se preferite lanciarlo a mano:

> Usa l'agente `scenario-researcher`. Trova e documenta le barriere di accessibilità reali
> sulla procedura di domanda dell'Assegno Unico INPS, per una persona cieca che usa NVDA e
> solo la tastiera. Produci `src/main/resources/scenarios/inps-assegno-unico.json` e
> `docs/EVIDENCE.md`. Leggi prima `docs/PERSONA.md`. Il campo `sourceFacts` deve contenere,
> **letterale**, ogni numero che la demo pronuncerà.

### Verifica prima di proseguire

```bash
python -c "import json;d=json.load(open('src/main/resources/scenarios/inps-assegno-unico.json',encoding='utf-8'));print(len(d['barriers']),'barriere,',len(d['sourceFacts']),'fatti,',len(d['procedure']),'passi')"
```

Attesi almeno: 4 barriere, `sourceFacts` non vuoto, procedura fino al protocollo.

**Se `sourceFacts` è vuoto, fermatevi e fatelo completare.** Il gate di fidelity verifica i
numeri contro quella lista: se è vuota, approva qualunque cosa e sembra verde. Un controllo
che passa sempre è peggio di nessun controllo, perché vi fa credere di essere coperti.

---

## Passo 2 — i contratti condivisi (~45 min)

**Un agente solo. Nessun parallelismo qui.**

> Usa l'agente `contract-architect`. Definisci i contratti condivisi di VisioVoice secondo
> il tuo brief. Leggi `docs/PROVENANCE-SPEC.md` (normativa) e lo scenario prodotto al passo
> precedente: i tipi devono saper rappresentare tutto ciò che c'è nello scenario. Consegna
> `mvn -q test` verde con `ProvenanceInvariantTest`. Nel report finale elenca le **firme
> esatte** che i builder dovranno implementare.

### Verifica

```bash
mvn -q test && echo "CONTRATTI OK"
```

### Il passaggio da non saltare

Il report dell'agente contiene le firme dei metodi. **Copiatele nella sezione "Firme" di
[`docs/ARCHITECTURE.md`](ARCHITECTURE.md).**

Il report di un subagente vive solo dentro quella conversazione: alla fine della sessione
quel testo non c'è più. Se le firme restano lì, i quattro builder del passo 3 riceveranno
descrizioni a parole invece di contratti, e si inventeranno quattro vocabolari diversi.
Due minuti di copia-incolla ora valgono un'ora di merge risparmiata.

---

## Passo 3 — i quattro builder in parallelo (~1h45)

```
/vv-build
```

Oppure a mano, **tutti in un solo messaggio**:

> Lancia in parallelo, in un unico messaggio, questi quattro agenti. Includi in ogni prompt
> le firme esatte da `docs/ARCHITECTURE.md`.
>
> - `perception-engineer` → `perception/`
> - `narration-engineer` → `narration/`, `llm/`, `resources/fixtures/`
> - `procedure-engineer` → `procedure/`, `validation/`
> - `a11y-frontend` → `resources/static/visiovoice.js` + `extension/` (involucro MV3)
>
> A ciascuno aggiungi: leggi `CLAUDE.md` e `docs/PERSONA.md`, rispetta l'invariante di
> `docs/PROVENANCE-SPEC.md`, i dati di dominio si leggono da
> `src/main/resources/scenarios/` e non si scrivono nel codice, non toccare file fuori dal
> tuo perimetro, consegna `mvn -q test` verde. Se ti serve un tipo che non esiste,
> segnalalo invece di crearlo.

### Cosa fate voi mentre lavorano

Gli hook si occupano della compilazione e degli invarianti. A voi restano due cose:

1. **Leggete le richieste di tipi mancanti nei report.** Se due builder chiedono lo stesso
   campo, i contratti hanno un buco: aggiungetelo voi in `model/`, una volta, e avvisate
   entrambi. Se lasciate che se lo aggiungano da soli, lo fanno in due modi diversi.
2. **Non accettate "fatto" sulla parola.** Verificate voi:

```bash
mvn -q test && echo "TUTTI I TEST VERDI"
mvn spring-boot:run
```

Poi aprite **`http://localhost:8080/demo/`** — non la radice. È la replica del servizio, con
`visiovoice.js` già incluso: è il percorso della demo e non richiede alcun setup.

### Provare anche il veicolo "estensione" (facoltativo, 2 minuti)

Serve solo a mostrare la forma vera del prodotto. Su questa macchina le policy aziendali non
lo impediscono: i blocklist di Chrome ed Edge elencano ID specifici, non un blocco generale.

1. `chrome://extensions` (o `edge://extensions`)
2. attivate **Modalità sviluppatore**
3. **Carica estensione non pacchettizzata** → scegliete la cartella `extension/`
4. ricaricate `http://localhost:8080/demo/`

Il comportamento deve essere **identico** al veicolo incluso: è lo stesso file. Se differisce,
qualcuno ha duplicato la logica dentro `extension/` — va rimossa, o le due copie divergeranno.

---

## Passo 4 — il gate (~30 min, poi si ripete)

```
/vv-gate
```

Esegue i controlli automatici e lancia `fidelity-auditor`, che è in sola lettura: trova i
problemi e dice **quale agente** deve correggerli, ma non li corregge.

### I quattro controlli che nessun automatismo fa per voi

Questi li fate a mano, una volta. Sono quelli che contano.

- [ ] **Percorso da sola tastiera, mouse fisicamente scollegato.** Arrivate al protocollo?
      Se no, il requisito più importante del prodotto non è soddisfatto.
- [ ] **Prendete 3 numeri che il sistema pronuncia e ritrovateli a mano nella fonte.**
      Questo è il controllo del controllore: un gate con un errore di normalizzazione
      approva tutto e sembra verde. È il punto che salta sempre, e vale più di tutti.
- [ ] **Leggete ad alta voce le 5 frasi principali.** Suonano come una persona che aiuta o
      come un manuale? Se la seconda, il tono va riscritto.
- [ ] **Provate un IBAN sbagliato.** L'errore si sente, e si capisce senza vedere lo schermo?

### Assegnazione delle correzioni

Ogni riscontro va al **proprietario del perimetro**, mai "a chi capita":

| Ambito | Chi corregge |
|---|---|
| parsing, barriere | `perception-engineer` |
| testo parlato, provenance, fixture | `narration-engineer` |
| validatori, coaching, riepilogo | `procedure-engineer` |
| tastiera, ARIA, annunci | `a11y-frontend` |
| tipi condivisi | voi, in `model/` |
| scenario, `sourceFacts` | `scenario-researcher` |

Poi **rilanciate `/vv-gate`**. Un gate eseguito una volta misura l'istante in cui l'avete
eseguito, non lo stato in cui consegnate.

### Punto di ritorno

```bash
git add -A && git commit -m "gate verde" && git tag gate-1
```

---

## Passo 5 — la demo (~30 min)

```
/vv-demo
```

Produce `docs/DEMO.md`, `docs/AI-CONTRIBUTION.md`, `docs/DEMO-FALLBACK.md`.

### Le due prove finali

**1. Offline.** Spegnete il wifi e lanciate `mvn spring-boot:run`. Deve funzionare: il
client LLM mock è il default. Se non funziona, da qualche parte c'è una dipendenza dalla
rete ed è l'ultimo momento utile per scoprirlo.

**2. A voce, col cronometro, una volta intera.** Tre cose si scoprono solo provando:

- gli otto minuti sono sempre dodici: decidete **adesso** cosa tagliate
- il silenzio dopo `immagine` sembra un guasto: va annunciato
  (*"questo è tutto quello che Marco sente"*)
- chi parla mentre l'altro digita? Deciso prima, non sul momento

```bash
git tag demo-ok
```

---

## Come vi dividete in due

Il codice lo scrivono gli agenti. Il lavoro umano è **decidere e verificare**.

**Persona A — orchestrazione.** Lancia le skill, legge i report, colma i buchi nei
contratti, assegna le correzioni. È l'unica che tocca `model/` dopo il passo 2.

**Persona B — verifica reale.** Fa le quattro cose del passo 4 che nessun agente può fare:
tastiera senza mouse, numeri ritrovati a mano, tono letto a voce, NVDA se disponibile.

La persona B protegge dal rischio più concreto: un sistema con i test verdi che nessuno ha
mai provato come lo proverebbe Marco.

---

## Quando qualcosa non va

### Gli hook non scattano

Sintomo: scrivete un file con `"149,00 euro"` in `src/main/java/` e non viene bloccato.

Causa quasi certa: `.claude/settings.json` non esisteva all'avvio della sessione, quindi il
sorvegliante della configurazione non è mai partito. Skill e agenti compaiono comunque,
perché usano una scansione diversa — ed è proprio questo che trae in inganno.

Rimedio: **`/hooks`** oppure riavviare. In una sessione nuova il file c'è già all'avvio e
tutto si carica normalmente.

Per distinguere "hook non agganciato" da "hook scritto male", lanciatelo a mano:

```bash
printf '%s' '{"tool_input":{"file_path":"src/main/java/it/visiovoice/X.java"}}' \
  | bash .claude/hooks/invariant-guard.sh; echo "exit=$?"
```

`exit=2` con il messaggio di blocco = la logica è giusta, è l'aggancio che manca.

### `mvn` non trova le dipendenze

```bash
mvn -q dependency:go-offline
```

Se fallisce siete dietro un proxy. Controllate `~/.m2/settings.xml`.

### L'estensione non si carica, o si comporta diversamente

Non blocca la demo: il percorso principale è `http://localhost:8080/demo/`, dove
`visiovoice.js` è incluso dalla pagina e non dipende dal browser.

Se si carica ma **si comporta in modo diverso**, la causa è quasi sempre una di queste due:

```bash
grep -rn "chrome\." src/main/resources/static/visiovoice.js   # atteso: vuoto
ls extension/                                                  # atteso: solo manifest.json e loader.js
```

La logica non deve usare `chrome.*` (nel veicolo estensione gira nel contesto della pagina,
dove quelle API non esistono), e `extension/` non deve contenere una copia di
`visiovoice.js`: una seconda copia va fuori sincrono in poco tempo.

### Il submit della pagina non funziona più

Qualcuno ha sostituito un controllo del form invece di annotarlo. È la regola che
`a11y-frontend` non può violare: gli interventi sono **additivi o in-place**. Se il submit si
rompe, il protocollo finale torna a essere un numero inventato da noi, e il progetto perde
la cosa che lo distingue da una demo finta.

### Due agenti si sono sovrascritti a vicenda

Sono stati lanciati in parallelo sullo stesso package. Recuperate con `git diff`, poi
rilanciateli **in sequenza**. La tabella dei perimetri è in
[`docs/AGENT-TEAM.md`](AGENT-TEAM.md).

### Un agente dice "fatto" ma i test sono rossi

Non è fatto. Rimandatelo indietro citando l'output esatto:

> `mvn -q test` fallisce con questo output: [incollalo]. Sistemalo. Non dichiarare fatto
> ciò che non passa i test.

### Il gate di fidelity è verde ma i numeri sono sbagliati

Controllate `sourceFacts` nello scenario. Se è vuoto o normalizzato (`149` invece di
`"149,00 €"`), il gate non ha nulla contro cui confrontare e approva tutto. È il modo in cui
questo sistema può mentirvi: rimettete i valori **letterali** come appaiono nella fonte.

### Il tempo sta finendo

Si taglia in quest'ordine: `PdfPerceiver`, il calendario accessibile (un campo data
testuale per Marco è anche più rapido), il validatore di codice fiscale, la sintesi vocale
del browser, la vista prima/dopo affiancata.

**Mai, in nessuna circostanza:** il gate di fidelity e la navigabilità da sola tastiera.
Senza il primo il prodotto può dire cose false a Marco; senza il secondo lui non può
usarlo, e tutto il resto diventa una dimostrazione per sviluppatori.
