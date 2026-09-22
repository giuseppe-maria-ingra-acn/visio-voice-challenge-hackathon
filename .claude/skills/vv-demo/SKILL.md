---
name: vv-demo
description: Fase 5 di VisioVoice - prepara il copione della demo prima/dopo, la mappa del contributo AI generata dai trace e il piano B. Usala nell'ultima ora, quando il codice e' stabile e i gate sono verdi.
---

# Demo — confezionare ciò che si vedrà

## Prerequisito

```bash
mvn -q test && echo "GATE VERDI"
```

Se i test sono rossi, non sei in fase demo: sei in fase gate. Torna a `/vv-gate`.
Preparare il racconto di un sistema rotto è il modo migliore per perdere l'ultima ora due
volte — una per scrivere il copione, una per riscriverlo.

## 1. Raccogli i fatti prima di raccontarli

```bash
cat docs/.agent-activity.log 2>/dev/null   # quali agenti hanno lavorato e quando
ls docs/                                    # cosa esiste già
grep -c "AI_INFERRED\|AI_REPHRASED\|SOURCE_VERBATIM" -r src/main/java/ 2>/dev/null
```

Il log delle attività è stato scritto dall'hook `agent-activity` durante tutto lo sviluppo.
È la base della mappa del contributo AI: quei dati sono **registrati**, non ricordati, ed è
la differenza fra un documento verificabile e uno scritto a memoria la sera prima.

## 2. L'agente (agente: `demo-director`)

> Produci `docs/DEMO.md`, `docs/AI-CONTRIBUTION.md` e `docs/DEMO-FALLBACK.md` secondo il tuo
> brief. Ricava la mappa del contributo AI da `docs/.agent-activity.log` e dai
> `FidelityReport`, non a memoria. Usa i numeri veri: quanti segmenti per livello di
> provenance, quanti retrocessi dal gate.

## 3. La prova a voce, col cronometro

Questa la fate voi due, non un agente. Cronometro alla mano, una volta intera.

Tre cose si scoprono solo provando, e tutte e tre a freddo costano la demo:

- **Gli otto minuti sono sempre dodici.** Quello che si taglia va deciso adesso, non mentre
  parlate. Decidete ora cosa salta se siete in ritardo.
- **Il silenzio dopo `immagine` sembra un guasto.** Va annunciato: *"questo è tutto quello
  che Marco sente"*. Altrimenti il pubblico pensa che si sia rotto qualcosa e il momento
  più forte della demo si trasforma in imbarazzo.
- **Chi parla mentre l'altro digita?** Decidetelo prima. Due persone che si accavallano su
  una demo da 8 minuti ne bruciano due.

## 4. L'ultimo controllo: la rete

```bash
# Disattiva il wifi, poi:
mvn spring-boot:run
```

La demo **deve** funzionare offline. Se non lo fa, `MockLlmClient` non è il default da
qualche parte: trova il punto e sistemalo. È l'ultimo momento utile per scoprirlo.

## Criterio di uscita

- [ ] `DEMO.md` provato a voce con il cronometro, almeno una volta intera
- [ ] `AI-CONTRIBUTION.md` con numeri presi dai trace
- [ ] `DEMO-FALLBACK.md` con un comando pronto per ogni rischio
- [ ] il limite noto del gate è scritto (i numeri sì, la semantica no)
- [ ] provata offline, wifi spento
- [ ] un tag git sull'ultima versione verde, e sapete come tornarci

```bash
git tag demo-ok && echo "punto di ritorno creato"
```
