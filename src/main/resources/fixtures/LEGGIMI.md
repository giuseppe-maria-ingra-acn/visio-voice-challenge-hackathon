# Fixtures delle risposte dei modelli

Qui dentro stanno le risposte registrate che `MockLlmClient` usa al posto di un modello dal
vivo. Proprietario di questa cartella: **`narration-engineer`**. `contract-architect` ha
lasciato solo `selftest.txt`, che il test di determinismo usa per provare che il meccanismo
funziona.

## Come si chiama un file

Il nome del file e' il `promptId` della `LlmRequest`, con una di queste estensioni, provate in
quest'ordine: `.txt`, `.json`, `.md`.

```java
LlmRequest.of("descrivi-tabella-importi", prompt)   ->  fixtures/descrivi-tabella-importi.txt
```

Se il file non c'e', il client **non inventa**: restituisce un testo che dichiara la fixture
mancante, marcato `AI_INFERRED`. Una risposta verosimile al posto di una mancante sarebbe la
stessa allucinazione che il progetto esiste per prevenire, prodotta da noi invece che dal
modello.

## Segnaposto

Le variabili della richiesta si sostituiscono come `{{nome}}`:

```java
LlmRequest.of("x", prompt).withVariable("eco", "ciao")
```

## Perche' sono file di testo e non JSON strutturato

Perche' il contenuto va letto da una persona quando la demo dice qualcosa di strano, e perche'
il gate di provenance lavora sul testo. Se una fixture deve portare una tabella, si usa `.json`
e la deserializza chi la consuma.
