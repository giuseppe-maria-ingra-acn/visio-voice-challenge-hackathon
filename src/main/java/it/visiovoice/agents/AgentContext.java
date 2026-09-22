package it.visiovoice.agents;

import it.visiovoice.model.DetailLevel;
import it.visiovoice.model.Scenario;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Tutto cio' che un agente puo' sapere oltre al proprio input.
 *
 * <p>Immutabile e senza riferimenti a Spring: un agente si prova in un test con due righe di
 * costruzione e nessun framework. Nota cosa <b>non</b> c'e': lo stato di sessione mutabile e i
 * valori dei campi. I dati dell'utente stanno nel DOM della pagina, che e' la fonte di verita';
 * un agente che ne ricevesse una copia potrebbe raccontarla diversa da com'e'.
 *
 * @param sessionId a quale sessione appartiene questa invocazione
 * @param scenario  lo scenario con fatti e barriere: e' l'unica fonte di dati di dominio
 *                  ammessa. Nessun agente inventa importi
 * @param level     quanto racconto vuole l'utente adesso
 * @param options   parametri di passaggio fra orchestratore e agente, sempre stringhe: se
 *                  qualcosa qui dentro diventa strutturato, e' un campo che manca al contesto
 */
public record AgentContext(
        String sessionId,
        Scenario scenario,
        DetailLevel level,
        Map<String, String> options) {

    public AgentContext {
        Objects.requireNonNull(sessionId, "sessionId");
        level = level == null ? DetailLevel.STANDARD : level;
        options = Map.copyOf(options == null ? Map.of() : options);
    }

    public static AgentContext of(String sessionId, Scenario scenario, DetailLevel level) {
        return new AgentContext(sessionId, scenario, level, Map.of());
    }

    public Optional<String> option(String key) {
        return Optional.ofNullable(options.get(key));
    }

    public AgentContext withOption(String key, String value) {
        Map<String, String> merged = new java.util.LinkedHashMap<>(options);
        merged.put(key, value);
        return new AgentContext(sessionId, scenario, level, merged);
    }

    public AgentContext withLevel(DetailLevel newLevel) {
        return new AgentContext(sessionId, scenario, newLevel, options);
    }
}
