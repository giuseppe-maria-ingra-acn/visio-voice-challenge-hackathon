# Specifica dell'invariante di provenance

> Normativa. `contract-architect` la implementa, `fidelity-auditor` la verifica,
> tutti gli altri la rispettano. Il seme Java sta in `docs/reference/Provenance.seed.java`.

## Perche' esiste

Un utente vedente a cui l'AI racconta una balla **se ne accorge**: guarda lo schermo e
confronta. Marco non puo'. Se diciamo "ti spettano 199 euro" e in pagina c'era 149, lui
prende una decisione economica sbagliata e **non ha modo di scoprirlo**.

Quindi l'anti-allucinazione qui non e' una buona pratica ingegneristica: e' la funzione di
sicurezza del prodotto. E va implementata in modo **deterministico e testabile**, non
affidata al giudizio di un altro LLM.

## I quattro livelli

| Livello | Significato | Serve revisione umana? |
|---|---|---|
| `SOURCE_VERBATIM` | copiato letterale dalla pagina | no |
| `AI_REPHRASED` | riformulato, **ogni numero riancorato alla fonte** | no |
| `AI_INFERRED` | dedotto: un calcolo, un nesso, una spiegazione | **si'** |
| `HUMAN_REVIEWED` | una persona ha letto e approvato | no |

`weakest(a,b)` -> il livello peggiore. Componendo segmenti vince l'anello debole: unire una
frase letta a una dedotta produce **dedotto**. Non si rivendica la solidita' della parte
migliore.

## La regola meccanica (questo e' il gate)

Per ogni segmento marcato `AI_REPHRASED`:

1. Estrai dal testo tutti i **numeri** (`\d+(?:[.,]\d+)*`) e tutti i **nomi propri**
   (token che iniziano per maiuscola, non a inizio frase).
2. Normalizza: rimuovi separatori di migliaia, uniforma la virgola decimale, minuscolo.
3. Ogni token estratto **deve** comparire nel corpus della fonte (`sourceRefs`).
4. Se anche uno solo non compare -> **retrocedi** il segmento a `AI_INFERRED` e aggiungilo a
   `FidelityReport.unanchored`.

Nessuna chiamata a LLM in questo controllo. E' `String.contains` su testo normalizzato:
gira in microsecondi, e' riproducibile, e si testa con dati statici.

## Cosa NON pretende di fare

Questa regola prende i numeri inventati e i nomi inventati. **Non** prende una parafrasi
semanticamente sbagliata con gli stessi numeri ("entro il 30 giugno" -> "dopo il 30 giugno").

Questo limite va **dichiarato**, non nascosto: e' precisamente il punto in cui serve
revisione umana, e va scritto come tale. Il controllo semantico e' umano, ed e'
tracciato in `docs/AI-CONTRIBUTION.md`. Rivendicare di piu' sarebbe la stessa disonesta'
che stiamo cercando di prevenire.

## Casi di test obbligatori

`ProvenanceInvariantTest` deve coprire almeno:

| Caso | Input | Atteso |
|---|---|---|
| numero presente | fonte `"149,00 euro"`, testo `"149 euro"` | resta `AI_REPHRASED` |
| numero inventato | fonte `"149,00 euro"`, testo `"199 euro"` | -> `AI_INFERRED`, in `unanchored` |
| separatore migliaia | fonte `"17.200"`, testo `"17200"` | resta `AI_REPHRASED` |
| virgola decimale | fonte `"1.234,50"`, testo `"1234,50"` | resta `AI_REPHRASED` |
| nome proprio inventato | fonte senza `"Bologna"`, testo con `"Bologna"` | -> `AI_INFERRED` |
| composizione | `weakest(SOURCE_VERBATIM, AI_INFERRED)` | `AI_INFERRED` |
| verbatim non toccato | segmento `SOURCE_VERBATIM` con numeri | mai retrocesso |
| nessun numero | testo senza cifre ne' nomi | resta `AI_REPHRASED` |

## Effetto sull'interfaccia

- `AI_INFERRED` -> annuncio esplicito: *"questa parte l'ho dedotta io, conviene verificarla"*.
  Mai silenzioso. Marco deve poter decidere quanto fidarsi.
- Comando dedicato (`P`) -> l'utente chiede la provenienza dell'ultima frase e la sente.
- `FidelityReport` alimenta `docs/AI-CONTRIBUTION.md`: la mappa AI/umano non si scrive a
  mano la sera prima, **si genera dai trace**.
