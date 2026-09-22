package it.visiovoice.orchestrator;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.visiovoice.model.Scenario;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Gli scenari, caricati da {@code resources/scenarios} all'avvio.
 *
 * <p>Gli scenari sono dati, non codice: e' l'unico posto in cui un importo o una scadenza puo'
 * stare, perche' e' l'unico in cui porta con se' la propria citazione. Il gate di fidelity
 * riancora a questi fatti; un numero scritto in un {@code .java} non avrebbe nulla a cui
 * essere ricondotto.
 *
 * <p>Il caricamento e' tollerante sui campi che non conosce: uno scenario arricchito domani con
 * un campo nuovo deve continuare a caricarsi con il codice di oggi, altrimenti ogni aggiunta di
 * dati diventa una modifica di codice e i due lavori non possono procedere in parallelo.
 */
@Component
public class ScenarioStore {

    private static final String PATTERN = "classpath*:scenarios/*.json";

    private final Map<String, Scenario> scenarios = new LinkedHashMap<>();
    private final List<String> loadErrors;

    public ScenarioStore() {
        this(new PathMatchingResourcePatternResolver());
    }

    ScenarioStore(PathMatchingResourcePatternResolver resolver) {
        ObjectMapper mapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
        List<String> errors = new java.util.ArrayList<>();
        try {
            for (Resource resource : resolver.getResources(PATTERN)) {
                try (InputStream in = resource.getInputStream()) {
                    Scenario scenario = mapper.readValue(in, Scenario.class);
                    scenarios.put(scenario.id(), scenario);
                } catch (IOException | RuntimeException e) {
                    errors.add(resource.getFilename() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            errors.add("scenari non leggibili: " + e.getMessage());
        }
        this.loadErrors = List.copyOf(errors);
    }

    public Optional<Scenario> find(String scenarioId) {
        return scenarioId == null ? Optional.empty() : Optional.ofNullable(scenarios.get(scenarioId));
    }

    public List<Scenario> all() {
        return List.copyOf(scenarios.values());
    }

    /** Il primo scenario disponibile: quello che la demo apre se non ne viene chiesto uno. */
    public Optional<Scenario> defaultScenario() {
        return scenarios.values().stream().findFirst();
    }

    /**
     * Gli scenari che non si sono caricati, col motivo. Esposto in {@code /api/health}: un
     * caricamento fallito in silenzio si scopre in demo, quando il racconto e' vuoto e non si
     * capisce perche'.
     */
    public List<String> loadErrors() {
        return loadErrors;
    }

    public int count() {
        return scenarios.size();
    }
}
