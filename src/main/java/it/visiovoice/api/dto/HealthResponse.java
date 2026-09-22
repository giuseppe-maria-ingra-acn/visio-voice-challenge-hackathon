package it.visiovoice.api.dto;

import java.util.List;

/**
 * Lo stato del backend, in forma verificabile prima di una demo.
 *
 * <p>Dichiara anche cosa <b>manca</b>: quali strati non hanno ancora un'implementazione e quali
 * scenari non si sono caricati. Un endpoint di salute che risponde "ok" mentre meta' del
 * sistema non e' collegata non serve a nulla, e lo si scopre davanti al pubblico.
 */
public record HealthResponse(
        String status,
        String version,
        String llm,
        boolean llmFromFixtures,
        int scenarios,
        List<String> scenarioIds,
        List<String> missingAgents,
        List<String> scenarioLoadErrors) {
}
