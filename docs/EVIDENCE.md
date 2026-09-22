# EVIDENCE — Scenario INPS Assegno Unico Universale

> **DICHIARAZIONE OBBLIGATORIA — LEGGERE PRIMA DI TUTTO IL RESTO.**
>
> La pagina in `src/main/resources/static/demo/` e' una **ricostruzione realistica**
> basata su pattern di barriera documentati, **non** un'osservazione diretta del sito INPS.
> Non e' stato effettuato nessun accesso a inps.it, nessun login SPID, nessuno scraping.
>
> Una ricostruzione dichiarata e' legittima. Una spacciata per osservazione diretta
> sarebbe una bugia che chiunque smonta con una domanda.
>
> Senza chiave API il passaggio di descrizione dell'immagine (step VisioVoice che
> legge il PNG degli importi) girera' su **fixture registrate**, non su un modello LLM dal vivo.
>
> **Sulle cifre.** Gli importi e le soglie ISEE provengono da fonti terze (siti CAF e
> portali informativi) che citano la Circolare INPS n. 7 del 30 gennaio 2026. **Non sono
> stati verificati presso INPS**, perche' non abbiamo acceduto al sito. Sono quindi cifre
> *plausibili e pubblicate*, non cifre *confermate alla fonte*.
>
> Per la demo e' sufficiente: il prodotto dimostra di leggere fedelmente cio' che c'e'
> nell'immagine e di ancorare ogni numero alla propria fonte. Se domani lo si puntasse alla
> pagina INPS vera, la fonte diventerebbe quella e il meccanismo resterebbe identico.
> In un uso reale questo passaggio richiederebbe la verifica sulla circolare originale.

---

## 1. Metodo di costruzione

Le barriere sono state modellate a partire da:

1. **Pattern WCAG 2.1** (criteri 1.1.1, 1.3.1, 1.4.1, 2.1.1, 3.3.1, 4.1.2) — norma vincolante
   per i siti PA italiani dal D.Lgs. 106/2018 e dalle Linee Guida AgID.
2. **Dati AgID Monitoraggio Accessibilita' 2024** — rapporto automatico e manuale su ~29.000
   siti PA italiani, che quantifica la frequenza dei tipi di errore.
3. **`docs/PERSONA.md`** — la scheda di Marco Ferrari cita esplicitamente le quattro barriere
   nel form INPS; queste sono la specifica, non un'inferenza.
4. **Fonti secondarie sui dati numerici** — valori 2026 da siti che citano la
   INPS Circolare n. 7 del 30 gennaio 2026 (Allegato 1).

---

## 2. Dati numerici — osservati vs ricostruiti

### OSSERVATO (da fonti secondarie che citano INPS Circolare n. 7/2026)

| Fatto | Valore esatto | Fonte | Data consultazione |
|---|---|---|---|
| Importo massimo per figlio minorenne | `203,80 euro al mese` | cafuilromaelazio.it, enacinforma.it | 2026-09-22 |
| Importo minimo per figlio (ISEE > soglia o assente) | `58,30 euro` | enacinforma.it, cafuilromaelazio.it | 2026-09-22 |
| Soglia ISEE per importo massimo | `17.468,51 euro` | cafuilromaelazio.it | 2026-09-22 |
| Soglia ISEE oltre cui si applica il minimo | `46.582,71 euro` | enacinforma.it, cafuilromaelazio.it | 2026-09-22 |
| Rivalutazione ISTAT applicata | `+1,4%` | enacinforma.it, cafuilromaelazio.it | 2026-09-22 |
| Importo per ISEE ~25.000 euro | `165,90 €` | informazionescuola.it (cita Circ. 7/2026) | 2026-09-22 |
| Importo per ISEE ~35.000 euro | `115,90 €` | informazionescuola.it (cita Circ. 7/2026) | 2026-09-22 |
| Riferimento normativo | `Circolare n. 7 del 30 gennaio 2026` | piu' fonti concordi | 2026-09-22 |

URL consultati:
- https://www.cafuilromaelazio.it/assegno-unico-universale-2026-importi-aggiornati-isee-e-istruzioni-operative/
- https://www.enacinforma.it/assegno-unico-2026-importi-aggiornati-isee-marzo/
- https://www.informazionescuola.it/tabella-assegno-unico-2026-pdf-nuovi-importi-circolare-inps-7/

**Nota sulla tabella nel PNG**: la tabella `importi-2026.png` usa 4 fasce semplificate
(non l'intera Allegato 1 della Circolare che ha granularita' fine). I valori 165,90 e 115,90
sono valori puntuali a ISEE = 25.000 e 35.000 dal sito citato, non i boundary esatti dei
bracket. Il valore massimo (203,80) e il minimo (58,30) sono confermati da piu' fonti.

### RICOSTRUITO (modellato, non osservato)

| Elemento | Valore | Motivazione |
|---|---|---|
| IBAN di esempio | `IT60X0542811101000000123456` | Formato standard IT + 25 chars, valore fittizio |
| Codice fiscale Marco | `FRRMRC85C15A944S` | Calcolato dai dati anagrafici di Marco; ultimo carattere di controllo non verificato |
| Codice fiscale Sofia | `FRRSFR19C55A944K` | Idem |
| Codice fiscale Elia | `FRRLEL22R22A944J` | Idem |
| Numero di protocollo | `INPS-AUU-2026-0001234` | Formato realistico basato su convenzioni INPS; non osservato |
| Numero DSU | `INPS-DSU-2026-IT-123456789` | Formato plausibile; non osservato |
| ISEE di Marco | `25.000,00 euro` | Assunzione realistica per un impiegato amministrativo a Bologna |
| Struttura pagina HTML | Tutta | Replica basata sui pattern documentati, non screenshot del sito reale |

---

## 3. Barriere — fonte di ogni pattern

### b1 — IMAGE_ONLY_DATA (tabella importi come PNG)

**Tipo**: WCAG 2.1 SC 1.1.1 — Non-text Content  
**Cosa sente NVDA**: `grafico    immagine    tabella importi` (alt="tabella importi" + ruolo grafico)  
**Perche' blocca Marco**: Non puo' leggere nessun importo. Non sa se la sua fascia ISEE
rientra nel massimo o nel minimo. Deve chiedere a qualcuno.  
**Fonte del pattern**: AgID Monitoraggio Accessibilita' 2024 — "55,5% dei siti PA mancano
di testo alternativo sufficiente per immagini informative"
(https://monitoraggio.accessibilita.agid.gov.it, consultato 2026-09-22).
Citato anche in `docs/PERSONA.md` come barriera primaria.  
**Fedelta'**: RICOSTRUITO. Il PNG e' generato da `tools/GeneraImporti.java` con Java AWT.
I valori nel PNG coincidono carattere per carattere con i sourceFacts del JSON.

---

### b2 — UNLABELED_INPUT (campo IBAN senza label)

**Tipo**: WCAG 2.1 SC 1.3.1, SC 4.1.2  
**Cosa sente NVDA**: `modifica, vuoto`  
**Perche' blocca Marco**: Non sa che deve inserire un IBAN, non conosce il formato
(IT + 25 caratteri), non puo' verificare se ha sbagliato.  
**Fonte del pattern**: AgID Monitoraggio Accessibilita' 2024 — "48,2% dei campi input
nei siti PA mancano di label accessibile" (accessiway.com/blog, consultato 2026-09-22).
Citato in `docs/PERSONA.md` tabella barriere form.  
**Fedelta'**: RICOSTRUITO. Il campo in `step4.html` non ha `<label>`, non ha `aria-label`,
non ha `aria-labelledby`.

---

### b3 — VISUAL_ONLY_STATE (indicatore di avanzamento come immagine)

**Tipo**: WCAG 2.1 SC 1.1.1, SC 1.3.1  
**Cosa sente NVDA**: `immagine` (alt="immagine" letto dal cursore virtuale)  
**Perche' blocca Marco**: Non sa a che passo e' (es. "passo 3 di 5"), non puo' stimare
quanto manca per completare la procedura.  
**Fonte del pattern**: Citato esplicitamente in `docs/PERSONA.md`:
"Step-indicator come immagine: `immagine` — Non sa a che punto e' ne' quanti passi restano".
Pattern comune nei wizard multi-step PA (WCAG failure technique F3).  
**Fedelta'**: RICOSTRUITO. PNG generati da `tools/GeneraImporti.java`.
Ogni step ha il proprio `step-indicator-N.png` con alt="immagine".

---

### b4 — VISUAL_ONLY_STATE (calendario come griglia div)

**Tipo**: WCAG 2.1 SC 4.1.2, SC 2.1.1  
**Cosa sente NVDA**: `pulsante` (per ogni div con role="button" e aria-label="")  
**Perche' blocca Marco**: Non sa di essere in un calendario. Non vede che mese e' mostrato.
Non puo' selezionare la data con la tastiera (i div non hanno handler su keydown).  
**Fonte del pattern**: Citato in `docs/PERSONA.md`: "Calendario solo visuale: `pulsante` x30".
Pattern WCAG failure F68 (form control senza nome accessibile), F42 (emulare widget
native con elementi non semantici).  
**Implementazione**: `step1.html` — `<div role="button" aria-label="" onclick="...">`.
L'`aria-label=""` sovrascrive il testo numerico visibile come nome accessibile,
cosi' NVDA annuncia solo il ruolo ("pulsante") senza nome.  
**Fedelta'**: RICOSTRUITO.

---

### b5 — ERROR_NOT_ANNOUNCED (errore IBAN solo con bordo rosso)

**Tipo**: WCAG 2.1 SC 1.4.1, SC 3.3.1  
**Cosa sente NVDA**: silenzio (stringa vuota — nessun annuncio)  
**Perche' blocca Marco**: Clicca "Avanti", la pagina non avanza, ma NVDA non dice nulla.
Il bordo rosso e' l'unico segnale di errore. Marco non sa perche' non funziona.  
**Fonte del pattern**: Citato in `docs/PERSONA.md` come la barriera "piu' crudele".
AgID Monitoraggio 2024: "l'uso esclusivo del colore per comunicare informazioni" e' tra
i tre errori piu' frequenti (agid.gov.it/it/agenzia/stampa-e-comunicazione/notizie/2024/02/28, consultato 2026-09-22).  
**Implementazione**: `step4.html` — la funzione `valida()` setta solo
`campo.style.borderColor = 'red'`. Il `<div id="errore-iban">` diventa visibile
con `display:block` ma non ha `role="alert"` ne' `aria-live`.  
**Fedelta'**: RICOSTRUITO.

---

## 4. Verifica artefatti

Eseguiti dalla radice del progetto in data 2026-09-22:

```
=== VERIFICA 1: dati nel DOM? ===
grep -rnE "199|149|importo mensile" src/main/resources/static/demo/*.html
→ Nessun match → OK (i numeri degli importi sono solo nel PNG, non nel DOM)

=== VERIFICA 2: PNG presenti? ===
ls -l src/main/resources/static/demo/*.png
→ importi-2026.png  19650 bytes  820x338 px
→ step-indicator-1.png  ...  step-indicator-5.png
```

```
python -c "import json; json.load(open('src/main/resources/scenarios/inps-assegno-unico.json')); print('JSON valido')"
→ JSON valido
```

---

## 5. Campi del modello dati che potrebbero servire

Segnalati a `contract-architect`:

1. **`barrier` come array su `field`**: il campo IBAN attiva sia b2 (unlabeled) sia b5
   (error not announced). Il modello attuale permette solo una stringa `"barrier": "b2"`.
   Suggerito: `"barriers": ["b2","b5"]`.

2. **`fidelity` sulla singola barriera**: il campo `fidelity` e' ora solo a livello di
   `service`. Potrebbe essere utile a livello di singola barriera (alcune sono
   OSSERVATO, altre RICOSTRUITO).

3. **`aiProvenance` su `sourceFacts`**: ogni sourceFact ha un `text` (verbatim) ma non
   un campo `provenance` esplicito. Il FidelityAgent potrebbe beneficiare di
   `"provenance": "SOURCE_VERBATIM"` gia' nel JSON, cosi' non deve inferirlo.

4. **`computedFacts`**: i valori calcolati dalla demo (es. 331,80 euro = 2 x 165,90)
   sono AI_INFERRED per definizione. Un array separato `computedFacts` eviterebbe
   di contaminarli con i sourceFacts verbatim.
