# Marco Ferrari — la persona per cui stiamo costruendo

> Questo file e' la fonte di verita' sulla persona. Se un agente deve decidere qualcosa e il
> codice non basta a rispondere, la risposta sta qui. Se anche qui non c'e', **chiedi** —
> non inventare un bisogno.

## Chi e'

**Marco Ferrari, 41 anni**, di Bologna. Cieco dalla nascita. Impiegato amministrativo.
Due figli: Sofia (7 anni) e Elia (4 anni).

**Come usa il computer**
- Screen reader **NVDA** in italiano, velocita' di sintesi alta (~380 parole/minuto: molto piu'
  veloce del parlato naturale — non rallentare per lui, lo rallenteresti).
- **Display braille** a 40 celle per rileggere con precisione codici e cifre.
- **Solo tastiera.** Nessun mouse, nessun trackpad. Mai.
- Competenza informatica **alta**: naviga per landmark, usa la lista dei titoli (`H`), salta
  di form field in form field (`F`). Non e' un utente confuso: e' un utente **bloccato**.

**Cosa NON e'**
Non e' anziano, non e' disorientato, non ha difficolta' cognitive. Non serve semplificargli
i concetti: serve **dargli accesso all'informazione** che la pagina nasconde in un'immagine.
Trattarlo come un incapace e' il modo piu' rapido per sbagliare questo progetto.

## Cosa sta cercando di fare

Presentare la **domanda di Assegno Unico e Universale** sul portale INPS, per i due figli.
Vuole sapere **prima** quanto gli spetta, per capire se conviene aggiornare l'ISEE.

Obiettivo raggiunto = domanda inviata, con protocollo, **da solo**.

## Dove si blocca oggi — le due barriere

### Barriera 1 — l'importo e' dentro un'immagine
La pagina informativa mostra gli importi mensili per fascia ISEE come **tabella
renderizzata in un'immagine** (`importi-2026.png`), con `alt="tabella importi"`.

Cosa sente Marco oggi:
```
grafico    immagine    tabella importi
```
Non c'e' nessun percorso alternativo: ne' tabella HTML, ne' PDF accessibile, ne' testo.
Per sapere quanto gli spetta **deve chiedere a una persona vedente**. E' esattamente la
dipendenza che vogliamo togliere.

### Barriera 2 — il form lo perde per strada
Quattro problemi distinti, tutti reali e tutti frequenti nei portali PA:

| Problema | Cosa sente Marco | Perche' e' bloccante |
|---|---|---|
| Step-indicator come immagine | `immagine` | Non sa a che punto e' ne' quanti passi restano |
| Campo IBAN senza `label` | `modifica, vuoto` | Non sa cosa scriverci |
| Calendario solo visuale | `pulsante` x30 | Non riesce a inserire una data |
| Errore segnalato col colore | *silenzio* | Invia il form e non sa perche' e' stato rifiutato |

L'ultimo e' il piu' crudele: il form si ricarica, Marco non sente nulla di nuovo, e non ha
modo di capire che c'e' un errore.

## Il percorso che vogliamo consegnargli

0. Apre **la pagina del servizio**, quella di sempre. Non una seconda applicazione:
   VisioVoice lavora dentro quella pagina e la ripara.
1. Sente **cosa c'e'**: quante sezioni, quanti passi, cosa serve.
2. Chiede la tabella degli importi. La sente **come dati**, non come "immagine":
   fasce, cifre, e la fascia in cui ricade lui.
3. Comincia la procedura. A ogni campo sente **cosa scrivere, in che formato, con un esempio**.
4. Sbaglia l'IBAN. Lo sente **subito**, con il motivo preciso, non dopo l'invio.
5. Prima di inviare sente il **riepilogo** di cio' che ha compilato.
6. Invia **il form del sito**, non una nostra copia. Sente il **protocollo** che il servizio
   gli restituisce, cifra per cifra, e se lo rilegge in braille.

Ogni passo indica anche **da dove viene** l'informazione: letta dalla pagina, riformulata,
o dedotta. Il punto 2 e' quello che nessun altro strumento gli da' oggi.

## Come misuriamo se funziona

- [ ] Marco arriva al protocollo **senza chiedere aiuto a nessuno**, e senza uscire dalla
      pagina del servizio
- [ ] Ogni cifra pronunciata e' ritrovabile nella fonte (`ProvenanceInvariantTest`)
- [ ] Percorso completo **da sola tastiera**, mouse scollegato
- [ ] Ogni cambio di stato e' **annunciato** (`aria-live`), non solo mostrato
- [ ] Cio' che l'AI ha dedotto e' **dichiarato**, non mascherato da certezza
