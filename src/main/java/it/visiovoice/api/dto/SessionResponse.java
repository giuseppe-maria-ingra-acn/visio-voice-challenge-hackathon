package it.visiovoice.api.dto;

import it.visiovoice.model.DetailLevel;
import it.visiovoice.model.Phase;

/**
 * La sessione appena aperta.
 *
 * @param goal cosa l'utente sta cercando di ottenere, in una frase pronunciabile: e' la prima
 *             cosa che conviene fargli sentire, perche' conferma che siamo sulla procedura
 *             giusta
 */
public record SessionResponse(
        String sessionId,
        String scenarioId,
        String goal,
        Phase phase,
        int currentStep,
        int totalSteps,
        DetailLevel level) {
}
