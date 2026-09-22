# VisioVoice — speech di 5 minuti

> Da leggere ad alta voce. Frasi corte, parole facili, niente sigle non spiegate.
> Circa **700 parole** = 5 minuti a ritmo tranquillo, con le pause segnate.
> Fra parentesi quadre c'è **cosa fare**, non cosa dire.

---

## 0:00 — Apertura (40 secondi)

Buongiorno. Vi faccio sentire una cosa, prima di spiegarvi qualsiasi altra cosa.

[*pausa*]

Questa è una pagina di un servizio pubblico. Al centro c'è una tabella con gli importi:
quanto ti spetta, in base al tuo reddito. È il dato più importante della pagina.

Ecco tutto quello che sente una persona cieca, con il suo lettore di schermo:

**«immagine».**

[*pausa di due secondi*]

Una parola. Fine. Quella tabella è una fotografia, e una fotografia non si può leggere.

Per sapere quanto gli spetta, quella persona deve chiedere a qualcuno che vede.
Per un dato pubblico. Su un sito pubblico.

Noi abbiamo costruito **VisioVoice**, e serve esattamente a togliere quella dipendenza.

---

## 0:40 — Chi è Marco (40 secondi)

Abbiamo lavorato per una persona precisa. Si chiama Marco, ha 41 anni, è cieco dalla nascita.
Deve presentare la domanda dell'Assegno Unico per i suoi due figli.

Marco non è un utente confuso. È molto competente: usa il lettore di schermo a
380 parole al minuto — quasi il triplo di come vi sto parlando io — e usa solo la tastiera,
mai il mouse.

Non gli serve che gli semplifichiamo i concetti. **Gli serve accedere a un'informazione
che la pagina nasconde dentro un'immagine.** È un utente bloccato, non un utente lento.

---

## 1:20 — Cosa fa VisioVoice (70 secondi)

[*mostra la demo: la pagina prima, poi la pagina con VisioVoice attivo*]

VisioVoice non è un'altra applicazione dove ricopiare tutto. È un livello che entra
**dentro la pagina che Marco stava già usando**, e la ripara mentre lui la usa.

Quattro esempi, tutti nella demo che vedete:

**Uno.** La tabella-fotografia diventa una tabella vera, che si può navigare riga per riga.
E la voce gli dice: «con il tuo reddito ricadi nella terza fascia: 149 euro al mese per figlio».

**Due.** Il campo dove scrivere l'IBAN non ha un'etichetta. Marco sentiva solo
«casella di testo, vuota». Ora sente cosa scriverci, quanti caratteri servono, e un esempio.

**Tre.** Sbaglia una cifra dell'IBAN. Prima: silenzio. Premeva «invia» e la domanda veniva
rifiutata, senza sapere perché. Ora lo sente subito, con il motivo.

**Quattro.** Lo stato di avanzamento era un disegno. Ora è una frase:
«passo 2 di 5, dati dei figli, restano tre passi».

E alla fine Marco invia **il form del sito**, non una nostra copia. Il numero di protocollo
che sente è quello vero, che il servizio gli restituisce. Noi non ci mettiamo in mezzo.

---

## 2:30 — L'idea che tiene in piedi tutto (60 secondi)

Ora la parte di cui siamo più orgogliosi. [*pausa*]

Marco **non può controllare quello che gli diciamo**. Non vede lo schermo. Se noi sbagliamo
un importo, lui prende una decisione economica sbagliata e non ha nessun modo di accorgersene.

Quindi abbiamo messo una regola sopra tutto il resto:

> **Ogni frase che l'utente ascolta dichiara da dove viene. E ogni numero pronunciato
> deve essere ritrovabile nella fonte.**

Funziona così. Se una frase è copiata dalla pagina, è "fonte diretta".
Se l'intelligenza artificiale l'ha riscritta con parole più semplici, prima di pronunciarla
un controllo va a ricercare **ogni singolo numero** nella pagina di origine.

Se ne manca anche solo uno, la frase **viene retrocessa**, e Marco sente:
«attenzione, questa parte l'ho dedotta io, conviene verificarla».

Due cose importanti. La prima: questo controllo **non usa l'intelligenza artificiale**.
Un modello che controlla un altro modello ha gli stessi punti ciechi. Il nostro controllo
è un algoritmo: prende i numeri e li cerca. La seconda: non è una buona intenzione scritta
in un documento. È un test automatico, che gira a ogni modifica.

---

## 3:30 — Come l'abbiamo costruito (50 secondi)

Non abbiamo scritto questo codice a mano, e non l'abbiamo chiesto a un solo assistente generico.
Abbiamo costruito **una squadra di otto agenti specializzati**, e tre regole.

**Uno: ogni agente ha i suoi file e nessuno tocca quelli degli altri.** Così tre di loro
scrivono codice nello stesso momento senza pestarsi i piedi.

**Due: chi costruisce non è chi verifica.** L'agente che fa i controlli di qualità
**non ha il permesso di scrivere**. Se potesse sistemare i problemi da solo, perderebbe la
voglia di cercarli a fondo.

**Tre: le regole non sono raccomandazioni, sono blocchi automatici.** Se un agente prova a
scrivere un importo dentro il codice, la scrittura **viene bloccata**. Se rompe la compilazione,
viene richiamato e deve rimediare.

Otto agenti, quattro blocchi automatici, 141 test.

---

## 4:20 — Perché va oltre questo caso (40 secondi)

Abbiamo usato l'INPS come **caso di esempio**, perché è concreto e verificabile.
Ma nel codice non c'è una riga che sappia cosa sia l'INPS.

Le barriere che affrontiamo sono **tipi**: un dato chiuso in un'immagine, un campo senza
nome, un errore detto solo col colore. Sono le stesse nella banca, nella bolletta,
nel portale della sanità, nel gestionale aziendale.

Per coprire un servizio nuovo si aggiunge un file di dati. Il motore non si tocca.

---

## 4:50 — Chiusura (20 secondi)

Abbiamo cominciato con una parola: «immagine».

Il nostro obiettivo è che Marco arrivi in fondo alla sua domanda **da solo**, sentendo dei
numeri di cui può fidarsi — e sapendo, frase per frase, quali vengono dalla pagina e quali
vengono da noi.

Grazie.

---
---

# Appendice — se fanno domande

Non fa parte dei 5 minuti. Risposte brevi, da tenere pronte.

**«I dati della demo sono veri?»**
La pagina della demo è una **ricostruzione dichiarata**: non siamo entrati su inps.it, nessun
login. Le barriere sono modellate su pattern WCAG documentati e sui dati del monitoraggio AgID.
Gli importi vengono da fonti che citano una circolare INPS: sono cifre pubblicate, non
confermate alla fonte. È scritto nel repository, in `docs/EVIDENCE.md`. Se puntassimo il
sistema alla pagina vera, la fonte diventerebbe quella e il meccanismo resterebbe identico.

**«Il controllo sui numeri non intercetta tutto, vero?»**
No, e lo dichiariamo. Intercetta i numeri e i nomi inventati. **Non** intercetta una frase
riscritta male con i numeri giusti — per esempio "entro il 30 giugno" che diventa
"dopo il 30 giugno". Quello resta revisione umana, ed è tracciato.

**«Serve una chiave API? Funziona senza rete?»**
Funziona senza rete e senza chiave. Il client del modello ha un mock deterministico su
risposte registrate, ed è il **default**. La demo non dipende dalla connessione della sala.

**«I test sono tutti verdi?»**
141 test, 133 verdi in questo momento. Gli 8 rossi sono nei validatori e in una configurazione,
sono noti e assegnati. L'invariante di provenance è verde.

**«Perché non React?»**
Perché il DOM che scriviamo è esattamente il DOM che il lettore di schermo legge.
Un framework che genera markup al posto nostro, in questo prodotto, è un rischio, non un aiuto.

**«Come si integra con NVDA?»**
Non si integra: non serve. NVDA legge l'albero di accessibilità che pubblica il browser.
Una tabella vera, un'etichetta e una regione "aria-live" **sono** l'integrazione — e valgono
anche per JAWS e VoiceOver.
