---
name: java-conventions
description: Convenzioni Java del progetto VisioVoice - record immutabili, niente Lombok, nomi dei package, stile dei test, gestione degli errori. Leggila prima di scrivere codice Java in questo progetto.
---

# Convenzioni Java

Riferimento condiviso. Serve a far sembrare il codice di quattro agenti diversi scritto da
una sola persona, che è una condizione per poterlo leggere in fretta quando qualcosa non va.

## Tipi di dato: `record`, mai Lombok

```java
public record ScriptSegment(
        String id,
        SegmentRole role,
        String text,
        Provenance provenance,
        List<String> sourceRefs) {

    public ScriptSegment {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(provenance, "provenance");
        sourceRefs = List.copyOf(sourceRefs == null ? List.of() : sourceRefs);
    }
}
```

Tre cose in quel costruttore compatto, tutte volute:

- `requireNonNull` su `provenance` rende l'invariante **impossibile da aggirare per
  distrazione**. Un default silenzioso sarebbe una falla proprio nell'unica funzione di
  sicurezza che il prodotto ha.
- `List.copyOf` rende il record davvero immutabile: senza, chi ti passa la lista può
  modificarla dopo, e l'immutabilità è finta.
- Niente Lombok: un annotation processor che non gira in un IDE mal configurato produce
  errori incomprensibili, e non è il momento di scoprirlo.

## Package: per responsabilità, non per tipo tecnico

```
it.visiovoice.perception     non  it.visiovoice.parser
it.visiovoice.narration      non  it.visiovoice.service
it.visiovoice.procedure      non  it.visiovoice.util
```

I perimetri degli agenti coincidono con i package. Un package `util` o `service` non ha
proprietario, e quindi diventa la discarica dove finisce tutto e dove due agenti si scontrano.

## Errori: non lanciare eccezioni verso l'utente

Un'eccezione che arriva all'interfaccia diventa una frase che una sintesi vocale legge a
Marco. `NullPointerException` letto a voce non è un messaggio: è un vicolo cieco.

```java
// no
throw new IllegalArgumentException("IBAN invalid: checksum mismatch");

// sì
return ValidationResult.invalid(
    "Questo IBAN non supera il controllo di validità: probabilmente c'è una cifra "
    + "sbagliata. Rileggilo e riprova.");
```

I valori di ritorno per ciò che l'utente può sbagliare; le eccezioni solo per i bug del
programma, che l'utente non deve mai sentire.

## Test: il nome dice cosa e perché

```java
@Test
void numeroNonPresenteNellaFonteRetrocedeAdAiInferred() { ... }

@Test
void separatoreDelleMigliaiaNonFaFallireLAncoraggio() { ... }
```

Non `testValidate1`. Quando un test si rompe alle quattro ore di lavoro, il nome è tutto
ciò che hai per capire in dieci secondi se è un problema vero o un test scritto male.

Un `assert` per concetto. Dati statici in `src/test/resources/`, **mai** chiamate di rete
in un test: un test che dipende dalla rete non è un test, è una scommessa.

## Spring: costruttore, non `@Autowired` sui campi

```java
@Service
public class NarrationAgent implements Agent<ScreenModel, SpokenScript> {
    private final LlmClient llm;

    public NarrationAgent(LlmClient llm) {   // niente @Autowired: implicito e sufficiente
        this.llm = llm;
    }
}
```

Le dipendenze sul costruttore si vedono nella firma e si sostituiscono nei test senza
framework. I campi iniettati si possono dimenticare di inizializzare, e lo scopri a runtime.

## Nomi in italiano o in inglese?

**Codice in inglese, testo per l'utente in italiano.** I nomi di tipi, metodi e variabili
in inglese; i contenuti di `ScriptSegment.text`, `FieldGuidance` e `ValidationResult` in
italiano, perché li ascolta Marco.

I nomi dei test in italiano sono un'eccezione voluta: descrivono un comportamento del
prodotto, e in italiano si leggono più in fretta sotto pressione.
