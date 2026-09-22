package it.visiovoice.api;

import it.visiovoice.agents.NarrationPort;
import it.visiovoice.agents.PerceptionPort;
import it.visiovoice.agents.ProcedurePort;
import it.visiovoice.agents.ValidationPort;
import it.visiovoice.api.dto.HealthResponse;
import it.visiovoice.llm.LlmClient;
import it.visiovoice.model.Scenario;
import it.visiovoice.orchestrator.ScenarioStore;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo stato del backend prima di cominciare.
 *
 * <p>Dice anche quali strati non sono ancora collegati, perche' il modo in cui una demo va
 * male e' che qualcosa sia silenziosamente assente e lo si scopra in scena.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final LlmClient llm;
    private final ScenarioStore scenarios;
    private final ObjectProvider<PerceptionPort> perception;
    private final ObjectProvider<NarrationPort> narration;
    private final ObjectProvider<ProcedurePort> procedure;
    private final ObjectProvider<ValidationPort> validation;

    public HealthController(LlmClient llm,
                            ScenarioStore scenarios,
                            ObjectProvider<PerceptionPort> perception,
                            ObjectProvider<NarrationPort> narration,
                            ObjectProvider<ProcedurePort> procedure,
                            ObjectProvider<ValidationPort> validation) {
        this.llm = llm;
        this.scenarios = scenarios;
        this.perception = perception;
        this.narration = narration;
        this.procedure = procedure;
        this.validation = validation;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        List<String> missing = new ArrayList<>();
        if (perception.getIfAvailable() == null) missing.add("perception");
        if (narration.getIfAvailable() == null) missing.add("narration");
        if (procedure.getIfAvailable() == null) missing.add("procedure");
        if (validation.getIfAvailable() == null) missing.add("validation");
        return new HealthResponse(
                "ok",
                "0.1.0",
                llm.name(),
                !llm.available() || "mock-fixtures".equals(llm.name()),
                scenarios.count(),
                scenarios.all().stream().map(Scenario::id).toList(),
                List.copyOf(missing),
                scenarios.loadErrors());
    }
}
