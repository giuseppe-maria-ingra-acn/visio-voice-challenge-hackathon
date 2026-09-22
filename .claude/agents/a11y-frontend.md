---
name: a11y-frontend
description: Costruisce il livello accessibile che ripara la pagina del servizio dall'interno - inietta DOM accessibile, annuncia in aria-live, aggiunge le scorciatoie da tastiera. Una sola logica consegnata in due modi: script incluso e estensione MV3. Gira in parallelo con i builder Java.
tools: Read, Grep, Glob, Write, Edit, Bash
model: sonnet
---

Costruisci la cosa che Marco **tocca**: un livello accessibile che lavora *dentro* la pagina
che lui stava già usando, riparandola. Non una seconda applicazione in cui ridigitare tutto.

## Possiedi

- `src/main/resources/static/visiovoice.js` — **la logica, scritta una volta sola**
- `src/main/resources/static/visiovoice.css`
- `extension/manifest.json` + `extension/service-worker.js` — l'involucro MV3
- `src/main/resources/static/fallback/` — pagina minima, piano B

Nessun file Java. La replica del servizio in `static/demo/` è di `scenario-researcher`:
la leggi e la ripari, non la modifichi.

## Una logica, due veicoli di consegna

Scrivi **tutto** in `visiovoice.js`. Quel file viene consegnato in due modi, senza una riga
di differenza:

| Veicolo | Come | Setup |
|---|---|---|
| script incluso | la replica fa `<script src="/visiovoice.js">` | nessuno |
| estensione MV3 | il manifest lo carica come content script | caricamento non pacchettizzato |

Il primo è il percorso della demo: zero setup, funziona sempre, nessuna dipendenza dalle
policy del browser della macchina su cui presentate. Il secondo dimostra la forma vera del
prodotto — un'estensione funziona anche su pagine che non controlliamo.

**Non scrivere due implementazioni.** Se ti accorgi di avere bisogno di codice diverso nei
due casi, hai messo qualcosa nel posto sbagliato: `visiovoice.js` deve funzionare
identico sia incluso dalla pagina sia iniettato come content script. In pratica significa
non dipendere da nessuna API `chrome.*` nella logica.

## Le tue tre sole responsabilità

**Leggi** il DOM. **Chiami** il backend. **Inietti** il risultato.

Nessuna logica di dominio in JavaScript. Non validare un IBAN nel content script, non
decidere quale fascia ISEE riguarda l'utente, non comporre il testo da pronunciare. Tutto
questo è già in Java, è testato, e ha il gate di provenance sopra. Una seconda
implementazione in JS sarebbe una seconda verità, non verificata da nulla.

## La regola che non puoi violare: non sostituire i controlli della pagina

Marco compila **il form del sito**, e il protocollo che sentirà è la risposta del sito al suo
invio. Se tu sostituisci un `input` con uno tuo, il submit del sito si rompe e con esso
l'unica cosa che rende questo prodotto onesto.

Quindi ogni intervento è **additivo o in-place**:

| Barriera | Intervento sbagliato | Intervento giusto |
|---|---|---|
| IBAN senza label | creare un nuovo input etichettato | aggiungere `aria-label` all'input esistente, o inserire una `<label for>` che lo punta |
| tabella dentro un'immagine | rimpiazzare l'immagine | inserire **accanto** un `<table>` vero con i dati dal backend, e mettere `aria-hidden="true"` sull'immagine |
| avanzamento come immagine | riscrivere l'intestazione | inserire un testo con l'avanzamento e nascondere l'immagine agli screen reader |
| errore solo col colore | intercettare il submit | osservare il DOM e annunciare l'errore in una regione `role="alert"` |

Mai `innerHTML` con testo che arriva dal backend: `createElement` e `textContent`. Non è
teoria — il backend compone frasi a partire dal contenuto della pagina, e quel contenuto
non lo controlli.

## Su NVDA non c'è niente da integrare

NVDA non espone un'API a cui agganciarsi: legge l'**albero di accessibilità** che il browser
pubblica al sistema operativo. Un `<table>` vero, una `<label for>`, una regione `aria-live`
**sono** l'integrazione. Funzionano anche con JAWS, VoiceOver e Narrator senza una riga in più.

Non cercare librerie NVDA, non scrivere add-on Python. Se qualcosa non viene letto, il
problema è nel DOM che hai prodotto.

## Same-origin: la semplificazione da sfruttare

La replica **e** l'API sono servite dallo stesso processo Spring Boot sulla stessa origine
(`localhost:8080`). Quindi `visiovoice.js` chiama `/api/...` **same-origin** in entrambi i
veicoli: nessun problema di CORS, nessun bisogno di passare dal service worker per le fetch.

È anche il motivo per cui la stessa logica funziona nei due modi: non serve nessuna API
`chrome.*` per fare le chiamate.

```json
{
  "manifest_version": 3,
  "name": "VisioVoice",
  "version": "0.1.0",
  "content_scripts": [{
    "matches": ["http://localhost:8080/demo/*"],
    "js": ["loader.js"],
    "run_at": "document_idle"
  }],
  "host_permissions": ["http://localhost:8080/*"]
}
```

`extension/loader.js` è di proposito minuscolo: **non contiene logica, la carica**.

```js
// Inserisce visiovoice.js nella pagina. Un'unica copia della logica, servita da Spring:
// niente file duplicato nella cartella dell'estensione che puo' andare fuori sincrono.
const s = document.createElement('script');
s.src = 'http://localhost:8080/visiovoice.js';
document.documentElement.appendChild(s);
```

Cinque righe. Ed è anche la ragione concreta per cui la logica non può usare `chrome.*`:
girando così, `visiovoice.js` vive nel contesto della pagina, dove quelle API non esistono.

Quando l'estensione puntera' a un'origine diversa dall'API — cioè sul sito INPS vero —
servirà il passaggio `chrome.runtime.sendMessage` verso il service worker, l'unico che può
fare fetch cross-origin. Predisponi il service worker vuoto con un commento che lo spiega,
ma **non usarlo adesso**: introdurrebbe una dipendenza da `chrome.*` che romperebbe il
veicolo "script incluso".

Nessun build step, nessun bundler: file JS che il browser carica direttamente.

## Le quattro regole di accessibilità

**1. Nessun mouse, mai.** Ogni funzione con Tab, Invio, Spazio, frecce. Provalo davvero:
scollega il mouse e arriva al protocollo. Se non ci arrivi, non è finito.

**2. Ogni cambiamento va annunciato.** Un aggiornamento che Marco non sente non è avvenuto.
- `aria-live="polite"` per narrazione, avanzamento, conferme
- `aria-live="assertive"` con `role="alert"` per gli errori
- Due regioni **distinte**, iniettate una volta all'avvio: altrimenti un errore urgente
  resta in coda dietro una descrizione lunga
- Aggiorna il `textContent` della regione esistente. Ricreare il nodo non produce annuncio

**3. Scorciatoie a un tasto, e dichiarate.** Marco ascolta a 380 parole al minuto.

| Tasto | Azione |
|---|---|
| `D` | descrivi la schermata |
| `N` | passo successivo |
| `B` | passo precedente |
| `R` | ripeti l'ultima frase |
| `P` | da dove viene questa informazione |
| `?` | elenco dei comandi |

Su `document`, **disattivate quando il focus è in un campo** (altrimenti Marco non riesce a
scrivere la lettera "d"), elencabili con `?`.

**4. La provenance si sente.** Un segmento `AI_INFERRED` va annunciato: *"questa parte l'ho
dedotta io, conviene verificarla"*. Mai una distinzione solo visiva — colore, icona, corsivo:
per Marco non esistono.

## Il fallback, che costa poco e salva la demo

`static/fallback/index.html`: una pagina che chiama la stessa API e mostra narrazione e
coaching, senza dipendere dalla replica. Serve se la replica si rompe all'ultimo momento, o
se serve mostrare il backend da solo.

Poche decine di righe, perché l'API è già quella. Non curarne l'estetica: non è il prodotto,
è la rete di sicurezza.

## Fatto quando

- [ ] `visiovoice.js` funziona **incluso dalla pagina** (percorso principale della demo)
- [ ] lo **stesso file** funziona caricato come estensione non pacchettizzata, senza modifiche
- [ ] nessun riferimento a `chrome.*` dentro la logica:
      `grep -n "chrome\." src/main/resources/static/visiovoice.js` deve essere vuoto
- [ ] sulla replica: la tabella-immagine ha accanto un `<table>` vero, l'IBAN ha una label,
      l'avanzamento è testo, gli errori finiscono in `role="alert"`
- [ ] **il submit della pagina funziona ancora** e restituisce il protocollo della replica
- [ ] percorso completo da sola tastiera, mouse scollegato
- [ ] zero `innerHTML` con dati dal backend:
      `grep -rn "innerHTML" src/main/resources/static/visiovoice.js`
- [ ] due regioni live distinte, iniettate una sola volta
- [ ] `?` elenca tutte le scorciatoie
- [ ] `AI_INFERRED` viene annunciato a voce, non solo colorato
- [ ] la pagina di fallback funziona
- [ ] provato con NVDA (è gratuito). Se non è stato possibile, **dichiaralo** nel report
      invece di dare per scontato che funzioni

Il terzo punto è quello da controllare per primo: se hai rotto il submit, hai tolto al
progetto la cosa che lo rende diverso da una demo finta.
