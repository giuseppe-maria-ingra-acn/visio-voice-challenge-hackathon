# Come si testa

**Tutto gira su `localhost`. Nessun accesso a INPS, nessuno SPID, nessuna rete.**
La replica del servizio è servita dallo stesso Spring Boot, il client LLM è un mock su
fixture. Si può testare tutto a wifi spento — ed è un requisito, non una comodità.

---

## La cosa da sapere prima di tutto

I quattro livelli di test **non si coprono a vicenda**, e non hanno lo stesso valore:

| Livello | Automatizzabile | Cosa dimostra |
|---|---|---|
| 0. La barriera è vera | **sì**, un grep | che stiamo risolvendo un problema reale |
| 1. Unit Java | **sì**, `mvn test` | che il cervello calcola giusto |
| 2. API | **sì**, MockMvc | che gli strati si parlano |
| 3. DOM iniettato | **in parte**, pagina di self-check | che la riparazione della pagina avviene |
| 4. Accessibilità reale | **no** | **che Marco riesce a usarlo** |

Il livello 4 è l'unico che risponde alla domanda per cui esiste il progetto, ed è l'unico
che nessuno strumento può fare per voi. Un test verde ai livelli 1-3 con il livello 4 non
fatto significa: *"il sistema calcola correttamente cose che non sappiamo se servono"*.

Non mettete il livello 4 in fondo alla lista delle cose da fare.

---

## Livello 0 — la barriera e' vera?

Questo viene **prima** di tutto. Se la barriera e' finta, ogni test superiore verifica che il
sistema risolve un problema che non esiste.

La tabella degli importi nella replica deve essere un **PNG raster**. Se fosse un SVG con
`<text>`, o una tabella HTML nascosta con `aria-hidden`, i numeri sarebbero **nel DOM**: jsoup
li leggerebbe banalmente, nessun modello di visione servirebbe, e il passaggio centrale della
demo sarebbe teatro.

```bash
# Gli importi NON devono comparire nell'HTML della replica
if grep -rnE "199|149|importo mensile" src/main/resources/static/demo/*.html; then
  echo "PROBLEMA: i dati sono nel DOM, la barriera e' finta"
else
  echo "OK: i dati non sono nel DOM"
fi

# Deve esistere l'immagine, e l'HTML deve solo puntarla
ls -l src/main/resources/static/demo/*.png
grep -n "importi.*png" src/main/resources/static/demo/*.html
```

E il contrario: i valori disegnati nel PNG devono coincidere **carattere per carattere** con
`sourceFacts`, altrimenti il gate non riesce ad ancorarli e li retrocede — un falso allarme
che vi manda a cercare un bug che non esiste.

```bash
python -c "import json;d=json.load(open('src/main/resources/scenarios/inps-assegno-unico.json',encoding='utf-8'));[print(repr(f['text'])) for f in d['sourceFacts']]"
```

Confrontate quell'elenco con l'immagine, a occhio. Due minuti, una volta.

### E il submit funziona?

```bash
curl -si -X POST localhost:8080/demo/submit -d "iban=IT60X0542811101000000123456" | head -20
```

Deve rispondere con un numero di protocollo. Se non risponde, il protocollo finale della demo
lo inventiamo noi — e il prodotto perde la cosa che lo distingue da una dimostrazione finta.

---

## Livello 1 — unit test Java

```bash
mvn -q test                                   # tutto
mvn -q -Dtest=ProvenanceInvariantTest test    # solo il gate
mvn -q -Dtest=IbanValidatorTest test
```

`spring-boot-starter-test` è già nel `pom.xml`: porta JUnit 5, AssertJ, Mockito e MockMvc.
Non serve aggiungere niente.

### Cosa deve essere coperto qui

| Classe di test | Cosa verifica | Perché è automatizzabile |
|---|---|---|
| `ProvenanceInvariantTest` | gli 8 casi di `PROVENANCE-SPEC.md` | il gate è `String.contains` su testo normalizzato |
| `IbanValidatorTest` | mod-97-10, lunghezza per paese | algoritmo normato, vettori noti |
| `CodiceFiscaleValidatorTest` | carattere di controllo, omocodia | idem — usate codici **di fantasia** |
| `HtmlPerceiverTest` | le 7 posizioni della catena label | HTML statico in `src/test/resources/html/` |
| `BarrierDetectorTest` | ogni tipo di barriera: trovata **e** non trovata a vuoto | deterministico |
| `SpokenScriptTest` | panoramica ≤ 3 frasi | conteggio |
| `NoJargonTest` | nessun messaggio utente contiene gergo | grep sui testi prodotti |

`BarrierDetectorTest` deve avere, per ogni tipo di barriera, **due** test: uno che la trova
dove c'è, e uno che **non** la trova dove non c'è. Senza il secondo, un rilevatore che
restituisce sempre "barriera presente" passa tutti i test.

### Zero rete nei test

Nessun test chiama `inps.it`, e nessun test chiama un modello generativo. Il primo vi rende
dipendenti da un sito che non controllate; il secondo non verifica il vostro codice, verifica
il modello, e dà un risultato diverso a ogni esecuzione.

Verifica:

```bash
grep -rni "http" src/test/ | grep -vi "localhost\|127.0.0.1"   || echo "nessuna URL esterna nei test"
```

(`grep -E` non supporta i lookahead negativi, quindi si filtra in due passaggi invece di
scrivere una regex che sembra giusta e non lo è.)

---

## Livello 2 — l'API

```java
@SpringBootTest
@AutoConfigureMockMvc
class ApiSmokeTest {

    @Autowired MockMvc mvc;

    @Test
    void perceiveRestituisceUnoScriptConProvenanceSuOgniSegmento() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/html/demo-step1.html"));
        mvc.perform(post("/api/perceive").contentType(APPLICATION_JSON)
                .content("{\"html\":" + quote(html) + "}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.segments[*].provenance").exists())
           .andExpect(jsonPath("$.segments[?(@.provenance == null)]").isEmpty());
    }
}
```

L'ultima asserzione è quella che conta: **nessun segmento senza provenance**. È l'invariante
del prodotto verificato al confine dell'API, dove il frontend lo consuma.

Verifica a mano, con il server avviato:

```bash
curl -s localhost:8080/api/health
curl -s -X POST localhost:8080/api/guide/validate \
  -H "Content-Type: application/json" \
  -d '{"fieldId":"iban","value":"IT60X054281110100000012345"}'
```

Il secondo comando ha un IBAN di 26 caratteri invece di 27: la risposta deve contenere una
frase pronunciabile sulla lunghezza, **non** `"invalid"` né `"checksum mismatch"`.

---

## Livello 3 — il DOM iniettato

### Perché qui non ci sono unit test

Su questa macchina non c'è **nessun runtime JavaScript**: niente node, npm, bun o deno.
Quindi niente Jest, niente Playwright, niente axe-core via npm. Non è un problema da
risolvere installando roba a metà lavoro: è il motivo per cui la regola *"nessuna logica di
dominio in JavaScript"* esiste nel brief di `a11y-frontend`.

`visiovoice.js` fa tre cose — legge il DOM, chiama l'API, inietta il risultato. Se vi
accorgete di volerlo testare con dei casi limite, significa che ci è finita dentro della
logica che doveva stare in Java, dove è testabile.

### La pagina di self-check

Una pagina che gira nel browser, senza alcuno strumento: carica la replica in un `iframe`
(same-origin, quindi il DOM è leggibile), lascia lavorare `visiovoice.js`, poi verifica.

`src/main/resources/static/selfcheck.html`:

```html
<!doctype html>
<meta charset="utf-8">
<title>VisioVoice — self-check</title>
<h1>Self-check</h1>
<ul id="out"></ul>
<iframe id="f" src="/demo/" width="900" height="500"></iframe>
<script>
const out = document.getElementById('out');
function check(nome, esito) {
  const li = document.createElement('li');
  li.textContent = (esito ? 'PASS  ' : 'FAIL  ') + nome;
  li.style.color = esito ? 'green' : 'red';
  out.appendChild(li);
}

document.getElementById('f').addEventListener('load', () => {
  // visiovoice.js lavora al document_idle: diamogli un momento
  setTimeout(() => {
    const d = document.getElementById('f').contentDocument;

    check('esiste una tabella vera al posto dell-immagine',
          d.querySelectorAll('table th').length > 0);
    check('l-immagine dei dati e nascosta agli screen reader',
          !!d.querySelector('img[aria-hidden="true"]'));
    check('ogni input ha un nome accessibile',
          [...d.querySelectorAll('input:not([type=hidden])')].every(i =>
            i.getAttribute('aria-label') || i.labels?.length));
    check('esiste una regione live polite',
          !!d.querySelector('[aria-live="polite"]'));
    check('esiste una regione live assertive distinta',
          !!d.querySelector('[aria-live="assertive"]'));
    check('nessun elemento interattivo non nativo',
          d.querySelectorAll('div[onclick],span[onclick]').length === 0);
    check('il form della pagina e ancora integro',
          !!d.querySelector('form') && !!d.querySelector('form [type=submit]'));
  }, 1500);
});
</script>
```

Aprite `http://localhost:8080/selfcheck.html`: sette righe verdi o rosse in due secondi.

L'ultima verifica è la più importante: **il form della pagina è ancora integro.** Se
`a11y-frontend` ha sostituito un controllo invece di annotarlo, il submit del sito si rompe e
il protocollo finale torna a essere un numero inventato da noi.

---

## Livello 4 — accessibilità reale (manuale, e non delegabile)

### 4.1 Il percorso da sola tastiera

**Scollegate fisicamente il mouse.** Non "non usatelo": scollegatelo, perché altrimenti lo
usate senza accorgervene.

Poi, da `http://localhost:8080/demo/`, arrivate al protocollo usando solo:
`Tab`, `Shift+Tab`, `Invio`, `Spazio`, frecce, e le scorciatoie `D N B R P ?`.

- [ ] arrivo al protocollo
- [ ] ogni campo dice cosa vuole **prima** che io ci scriva
- [ ] l'errore sull'IBAN lo sento **prima** dell'invio
- [ ] il focus non resta mai intrappolato
- [ ] `?` elenca le scorciatoie
- [ ] `P` dice da dove viene l'ultima frase

Se non arrivate al protocollo, il progetto non ha soddisfatto il suo requisito principale, e
nessun test verde cambia questo fatto.

### 4.2 Con uno screen reader vero

NVDA è gratuito: [nvaccess.org](https://www.nvaccess.org/download/). Installatelo su una
macchina, anche solo per venti minuti.

Comandi minimi per provare: `Insert+Freccia giù` legge tutto, `H` salta di titolo in titolo,
`F` di campo in campo, `T` di tabella in tabella, `Insert+F7` apre la lista degli elementi.

- [ ] `T` trova la tabella degli importi — **prima** era un'immagine e non esisteva
- [ ] `F` sul campo IBAN annuncia un nome, non "modifica, vuoto"
- [ ] l'errore di validazione viene **annunciato** senza che io vada a cercarlo
- [ ] l'avanzamento ("passo 2 di 5") è leggibile come testo
- [ ] `AI_INFERRED` viene **detto**, non solo colorato

**Se non riuscite a provare con NVDA, scrivetelo** in `docs/AI-CONTRIBUTION.md` come limite
noto. "Non provato con uno screen reader" è un'informazione onesta; dare per scontato che
funzioni su un prodotto per non vedenti è la scorciatoia peggiore possibile.

### 4.3 Sostituto se NVDA non è installabile

Non equivalente, ma meglio di niente: in Chrome, `F12` → schede **Elements** → **Accessibility**
mostra l'albero di accessibilità e il nome calcolato di ogni elemento. Se il campo IBAN ha
`name: ""`, NVDA dirà "modifica, vuoto".

È questo l'albero che NVDA legge: se è giusto qui, è quasi certamente giusto anche lì.

---

## Il test del test: verificare il gate di fidelity

Il gate dichiara che ogni numero pronunciato è ritrovabile nella fonte. **Verificate il
verificatore**, perché un gate con un errore nella normalizzazione approva tutto e sembra
verde.

### Automatico — la fixture avvelenata

Aggiungete a `src/test/resources/fixtures/` una risposta LLM che contiene un importo
**assente** dallo scenario, e un test che pretende la retrocessione:

```java
@Test
void unImportoInventatoVieneRetrocessoESegnalato() {
    SpokenScript s = narrate(screenConFixture("importo-avvelenato.json"));
    FidelityReport r = fidelity.verify(s, scenario.sourceFacts());

    assertThat(r.unanchored()).isNotEmpty();
    assertThat(s.segmentById("seg-importo").provenance())
        .isEqualTo(Provenance.AI_INFERRED);
}
```

Questo è **il test da mostrare in demo**: è la prova che "semplificare senza tradire" non è
una promessa ma un controllo che gira.

### Manuale — tre numeri a mano

Prendete tre numeri che il sistema pronuncia e ritrovateli **con i vostri occhi** nella
pagina della replica. Non fidatevi del gate: il gate è codice, e anche il codice sbaglia.

Controllate in particolare `sourceFacts` nello scenario:

```bash
python -c "import json;d=json.load(open('src/main/resources/scenarios/inps-assegno-unico.json',encoding='utf-8'));[print(repr(f['text'])) for f in d['sourceFacts']]"
```

I valori devono essere **letterali** come appaiono nella pagina (`'149,00 €'`), non
normalizzati (`149.0`). Un valore normalizzato qui diventa un falso negativo nel gate.

---

## Cosa NON stiamo testando (da dichiarare, non da tacere)

| Non coperto | Perché | Chi lo copre |
|---|---|---|
| parafrasi sbagliata con numeri giusti | il gate ancora le cifre, non il significato | revisione umana, livello 4 |
| il sito INPS reale | mai contattato, per scelta | nessuno: la replica è dichiarata tale |
| browser diversi da Chrome/Edge | non disponibili qui | nessuno |
| screen reader diversi da NVDA | non disponibili qui | nessuno |
| carico, concorrenza | irrilevante per un assistente locale | nessuno |

Scrivere questa tabella è più solido che ometterla: un limite dichiarato è una scelta
tecnica, un limite taciuto è una svista che qualcuno troverà al posto vostro.

---

## Riassunto: i comandi

```bash
# 0. la barriera e' vera: gli importi non devono essere nel DOM
grep -rnE "199|149" src/main/resources/static/demo/*.html || echo "OK"

# 1. tutto il cervello Java
mvn -q test

# 2. solo il gate di provenance
mvn -q -Dtest=ProvenanceInvariantTest test

# 3. avvia e prova a mano
mvn spring-boot:run
#    http://localhost:8080/demo/         <- il percorso di Marco
#    http://localhost:8080/selfcheck.html <- le 7 verifiche sul DOM

# 4. la prova che conta: mouse scollegato, dal primo campo al protocollo

# 5. offline, come in demo
#    (wifi spento) mvn spring-boot:run
```

Se il punto 4 non è stato fatto, i punti 1-3 non dicono se il prodotto funziona.
