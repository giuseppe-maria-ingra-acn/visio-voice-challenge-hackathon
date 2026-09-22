---
name: vv-kickoff
description: Fase 0 e 1 di VisioVoice - fissa lo scenario reale con le sue prove, poi genera i contratti Java condivisi. Va eseguita per prima e una sola volta. Usala quando il progetto e' vuoto o quando manca src/main/java/it/visiovoice/model.
---

# Kickoff — dal foglio bianco ai contratti

Due passi **in sequenza**, non in parallelo. Il secondo dipende dal primo.

## Prima di cominciare

Verifica di essere davvero all'inizio:

```bash
ls src/main/resources/scenarios/ 2>/dev/null
ls src/main/java/it/visiovoice/model/ 2>/dev/null
```

Se i contratti esistono già, **fermati**: questa skill non va rieseguita. Rigenerare i
contratti mentre tre builder ci stanno lavorando sopra è il modo più rapido per perdere
un'ora. Usa `/vv-build` invece.

## Passo 1 — lo scenario (agente: `scenario-researcher`)

Lancia l'agente con un incarico esplicito:

> Trova e documenta le barriere di accessibilità reali sulla procedura di domanda
> dell'Assegno Unico INPS, per una persona cieca che usa NVDA e solo la tastiera.
> Produci `src/main/resources/scenarios/inps-assegno-unico.json` e `docs/EVIDENCE.md`.
> Leggi prima `docs/PERSONA.md`. Il campo `sourceFacts` deve contenere, **letterale**,
> ogni numero che la demo pronuncerà: è il corpus contro cui verrà verificata ogni cifra.

**Non passare oltre** finché non hai controllato di persona:

```bash
python -c "import json;d=json.load(open('src/main/resources/scenarios/inps-assegno-unico.json',encoding='utf-8'));print(len(d['barriers']),'barriere,',len(d['sourceFacts']),'fatti,',len(d['procedure']),'passi')"
```

Attesi: almeno 4 barriere, `sourceFacts` non vuoto, procedura completa fino al protocollo.
Se `sourceFacts` è vuoto, il gate di fidelity più avanti non avrà nulla contro cui verificare
e passerà **qualunque cosa**: un gate che approva sempre è peggio di nessun gate, perché
dà una falsa garanzia. Rimanda l'agente a completarlo.

## Passo 2 — i contratti (agente: `contract-architect`)

Un solo agente, da solo. Nessun parallelismo qui.

> Definisci i contratti condivisi di VisioVoice secondo il tuo brief.
> Leggi `docs/PROVENANCE-SPEC.md` (normativa) e lo scenario prodotto al passo 1: i tipi
> devono saper rappresentare tutto ciò che c'è nello scenario.
> Consegna `mvn -q test` verde con `ProvenanceInvariantTest`, e nel report finale le firme
> esatte che i builder dovranno implementare.

Al termine verifica tu:

```bash
mvn -q test && echo "CONTRATTI OK"
```

## Consegna del passo 2 — la cosa da non perdere

Il report di `contract-architect` contiene **le firme dei metodi**. Copiale in
`docs/ARCHITECTURE.md` se non ci sono già.

È ciò che finirà nel prompt dei tre builder paralleli: se resta solo nel report di un
subagente, quel testo va perso alla fine della sessione e i builder si inventeranno tre
vocabolari diversi. Cinque minuti di copia-incolla ora valgono un'ora di merge risparmiata.

## Dopo

`/vv-build` per lanciare i builder in parallelo.
