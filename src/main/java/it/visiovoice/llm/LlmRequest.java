package it.visiovoice.llm;

import java.util.Map;
import java.util.Objects;

/**
 * Una richiesta a un modello.
 *
 * <p>{@code promptId} e' obbligatorio e non e' un dettaglio di logging: e' la chiave con cui
 * {@link MockLlmClient} trova la fixture. Renderlo obbligatorio significa che non si puo'
 * scrivere una chiamata al modello senza aver deciso come si chiama la sua risposta registrata,
 * e quindi che non nasce una chiamata che funziona solo con la rete.
 *
 * @param promptId   nome della famiglia di prompt, e nome del file di fixture
 * @param system     istruzioni di sistema
 * @param user       il prompt vero
 * @param variables  valori sostituiti nel prompt. Stanno a parte per due motivi: il testo del
 *                   prompt resta confrontabile fra una chiamata e l'altra, e i dati di dominio
 *                   restano visibili invece di sparire dentro una stringa concatenata
 * @param maxTokens  tetto alla risposta
 */
public record LlmRequest(
        String promptId,
        String system,
        String user,
        Map<String, String> variables,
        int maxTokens) {

    public LlmRequest {
        Objects.requireNonNull(promptId, "promptId");
        Objects.requireNonNull(user, "user");
        variables = Map.copyOf(variables == null ? Map.of() : variables);
        maxTokens = maxTokens <= 0 ? 1024 : maxTokens;
    }

    public static LlmRequest of(String promptId, String user) {
        return new LlmRequest(promptId, null, user, Map.of(), 1024);
    }

    public LlmRequest withVariable(String key, String value) {
        Map<String, String> merged = new java.util.LinkedHashMap<>(variables);
        merged.put(key, value);
        return new LlmRequest(promptId, system, user, merged, maxTokens);
    }
}
