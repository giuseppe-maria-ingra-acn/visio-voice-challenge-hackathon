# VisioVoice

**Racconta a parole cosa c'è su una schermata, e accompagna passo per passo chi non può
vederla fino in fondo a una procedura online.**

Non è un lettore di schermo: quello esiste già e funziona bene. VisioVoice affronta il punto
in cui uno screen reader si ferma — quando l'informazione che serve **non è nel testo**, ma
dentro un'immagine, un grafico, un calendario visuale o un errore segnalato solo col colore.

---

## Il problema, in concreto

Marco ha 41 anni, è cieco dalla nascita, usa NVDA e un display braille. Deve presentare la
domanda di Assegno Unico per i suoi due figli.

Sulla pagina INPS gli importi mensili per fascia ISEE sono pubblicati come **immagine**.
Ecco tutto quello che il suo screen reader gli dice:

```
immagine
```

Per sapere quanto gli spetta deve chiedere a una persona che vede. Poi nel form trova il
campo IBAN senza etichetta (*"modifica, vuoto"*), lo stato di avanzamento disegnato in
un'immagine, un calendario che è una griglia di pulsanti senza nome, e un errore di
validazione segnalato **solo** colorando il bordo di rosso: invia, viene rifiutato, e non
sente nulla che gli spieghi perché.

Scheda completa: [docs/PERSONA.md](docs/PERSONA.md)

## Cosa fa VisioVoice

| | Prima | Dopo |
|---|---|---|
| Tabella importi | `immagine` | *"Con il tuo ISEE ricadi nella terza fascia: 149 euro al mese per figlio. Vuoi sentire tutte le fasce?"* |
| Campo IBAN | `modifica, vuoto` | *"IBAN del conto su cui ricevere il pagamento. 27 caratteri, comincia con IT. Esempio: IT60 X054 ..."* |
| IBAN sbagliato | *silenzio, poi rifiuto* | *"Questo IBAN non supera il controllo di validità: probabilmente c'è una cifra sbagliata."* |
| Avanzamento | `immagine` | *"Passo 2 di 5: dati dei figli. Restano tre passi."* |

E, su richiesta, dice sempre **da dove viene** quello che ha appena detto.

## L'idea portante: ogni frase dichiara la sua origine

Marco non può controllare quello che gli diciamo. Se sbagliamo un importo, lui prende una
decisione economica sbagliata e **non ha modo di accorgersene**.

Per questo ogni frase pronunciata porta con sé un livello di provenienza, e ogni numero
detto a voce deve essere **ritrovabile nella fonte**. Il controllo è deterministico — nessun
LLM che verifica un altro LLM — e gira come test:

| Livello | Significato |
|---|---|
| `SOURCE_VERBATIM` | copiato letterale dalla pagina |
| `AI_REPHRASED` | riformulato, ma ogni numero è stato riancorato alla fonte |
| `AI_INFERRED` | dedotto. Viene **annunciato come tale**: *"questa parte l'ho dedotta io"* |
| `HUMAN_REVIEWED` | una persona ha letto e approvato |

Se un solo numero non si ritrova nella fonte, il segmento **retrocede** a `AI_INFERRED` e
l'utente lo sente. Specifica e casi di test: [docs/PROVENANCE-SPEC.md](docs/PROVENANCE-SPEC.md)

**Limite dichiarato:** il controllo intercetta i numeri e i nomi inventati. *Non* intercetta
una parafrasi semanticamente sbagliata con i numeri giusti ("entro il 30 giugno" → "dopo il
30 giugno"). Quello resta revisione umana, ed è tracciato in `docs/AI-CONTRIBUTION.md`.

## Che forma ha

Un livello accessibile che **ripara la pagina dall'interno**, invece di essere una seconda
applicazione in cui ridigitare tutto. Marco compila il form del sito; il protocollo che sente
alla fine è la risposta del sito al suo invio, non un numero generato da noi.

```
Marco  ->  pagina del servizio  +  livello VisioVoice
                                        |  HTTP localhost:8080
                                        v
                       backend Java: percezione, narrazione,
                       gate di fidelity, validatori, provenance
                                        |
                                        v
                          DOM accessibile iniettato nella pagina
                                        |
                                        v
                   NVDA lo legge dall'albero di accessibilita'
```

Su NVDA non c'è nulla da integrare: non espone un'API, legge l'albero di accessibilità che il
browser pubblica. Un `<table>` vero al posto di un'immagine, una `<label>` sul campo IBAN e
una regione `aria-live` **sono** l'integrazione — e funzionano anche con JAWS e VoiceOver.

La stessa logica (`visiovoice.js`) è consegnata in due modi: inclusa dalla pagina (zero setup)
oppure caricata come estensione MV3 (la forma vera del prodotto, perché funziona anche su
pagine che non controlliamo). Il backend Java è identico.

## Come si sviluppa

Il progetto è costruito da una squadra di agenti specializzati, ognuno con un perimetro di
file suo e una definizione di "fatto" verificabile a macchina. Due invarianti sono imposti da
hook, non dalla buona volontà: **il codice Java deve compilare** e **i dati di dominio non
possono finire dentro il codice**.

```
.claude/agents/     8 agenti: ricerca, contratti, 3 builder paralleli, UI, audit, demo
.claude/skills/     i workflow di fase, invocabili come /comando
.claude/hooks/      i gate meccanici
docs/               persona, architettura, specifica provenance, piano
```

Mappa della squadra e ordine di lancio: [docs/AGENT-TEAM.md](docs/AGENT-TEAM.md)

## Avvio

**Tutti i passi, in ordine, con i comandi da copiare:
[docs/GUIDA-OPERATIVA.md](docs/GUIDA-OPERATIVA.md).**

Serve Java 17 e Maven. Nessuna chiave API: il client LLM ha un mock deterministico su
fixture, ed è il default — la demo non dipende dalla rete.

```bash
mvn -q test             # tutti i gate, invariante di provenance compreso
mvn spring-boot:run     # poi http://localhost:8080/demo/
```

Strategia di test completa, e cosa non e' automatizzabile:
[docs/COME-TESTARE.md](docs/COME-TESTARE.md)

## Stack

Java 17, Spring Boot 3.3.5, jsoup per il parsing HTML. Frontend in HTML semantico e
JavaScript vanilla, senza build step e senza framework: il DOM che scriviamo è esattamente
il DOM che lo screen reader legge.
