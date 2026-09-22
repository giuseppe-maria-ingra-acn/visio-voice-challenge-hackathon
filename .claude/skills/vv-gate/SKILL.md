---
name: vv-gate
description: Fase 4 di VisioVoice - esegue tutti i gate di qualita', lancia l'audit indipendente e produce l'elenco delle correzioni con il loro proprietario. Usala dopo ogni fase di build e obbligatoriamente prima della demo.
---

# Gate — la verifica indipendente

Strategia completa e limiti dichiarati: [`docs/COME-TESTARE.md`](../../../docs/COME-TESTARE.md)

## 1. I controlli automatici, tutti insieme

```bash
mvn -q test 2>&1 | tail -20
echo "--- invariante di provenance ---"
mvn -q -Dtest=ProvenanceInvariantTest test && echo "INVARIANTE OK"
echo "--- dati di dominio nel codice di produzione (atteso: nessuno) ---"
grep -rnE '"[^"]*[0-9]+([.,][0-9]+)?[^"]*(euro|EUR|mesi|giorni|ISEE)' src/main/java/ || echo "pulito"
echo "--- elementi non nativi nell'interfaccia (atteso: nessuno) ---"
grep -rn "onclick\|onmouseover" src/main/resources/static/ || echo "pulito"
echo "--- regioni live (attese: almeno 2, distinte) ---"
grep -rn "aria-live" src/main/resources/static/
```

## 2. L'audit indipendente (agente: `fidelity-auditor`)

> Esegui i tuoi sei controlli su tutto il progetto. Riferisci ordinando per danno a Marco.
> Per ogni riscontro indica `file:riga`, cosa succede a Marco in concreto, e quale agente
> deve correggerlo. Dichiara anche cosa **non** hai controllato.

Questo agente non ha `Write` né `Edit`: non può sistemare ciò che trova, e questo è
intenzionale. Un agente che corregge da sé i propri riscontri non ha più motivo di cercare
a fondo, e chi legge il report non distingue più fra codice pulito e codice ripulito in fretta.

## 3. La prova che nessun automatismo sostituisce

Il gate automatico verifica che i numeri siano ancorati. **Non** verifica che la frase abbia
senso. Questo pezzo è umano, e va fatto a mano una volta:

- [ ] Percorri la demo **da sola tastiera**, col mouse fisicamente scollegato. Arrivi al
      protocollo? Se no, il requisito più importante non è soddisfatto.
- [ ] Leggi ad alta voce le 5 frasi principali. Suonano come una persona che aiuta, o come
      un manuale? Se la seconda, il tono va riscritto.
- [ ] Prendi 3 numeri pronunciati dal sistema e **ritrovali a mano** nella pagina sorgente.
      Non fidarti del gate: verifica il gate.
- [ ] Prova un IBAN sbagliato. L'errore si sente, ed è comprensibile senza vedere lo schermo?

Il terzo punto è quello che salta sempre e che vale più di tutti: è il controllo del
controllore. Un gate con un bug nella normalizzazione passa qualunque cosa e sembra verde.

## 4. Assegnazione delle correzioni

Per ogni riscontro dell'audit, rimanda **all'agente proprietario del perimetro**, non a chi
capita:

| Ambito del riscontro | Chi corregge |
|---|---|
| parsing, rilevamento barriere | `perception-engineer` |
| testo parlato, provenance, fixture | `narration-engineer` |
| validatori, coaching, riepilogo | `procedure-engineer` |
| tastiera, ARIA, annunci | `a11y-frontend` |
| tipi condivisi | `contract-architect` (o l'orchestratore) |
| scenario, `sourceFacts` mancanti | `scenario-researcher` |

Poi **rilancia questa skill**. Un gate eseguito una volta sola misura il momento in cui
l'hai eseguito, non lo stato in cui consegni.

## Criterio di uscita

Zero riscontri `CRITICO` e zero `ALTO`. I `MEDIO` e `BASSO` si annotano in
`docs/AI-CONTRIBUTION.md` come limiti noti: dichiararli è più solido che tacerli, perché
un limite scritto è una scelta, un limite taciuto è una svista.
