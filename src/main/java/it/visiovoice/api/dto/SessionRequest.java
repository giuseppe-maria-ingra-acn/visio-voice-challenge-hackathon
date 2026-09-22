package it.visiovoice.api.dto;

import it.visiovoice.model.DetailLevel;

/** Apertura di sessione. Entrambi i campi sono opzionali: si applicano i valori di default. */
public record SessionRequest(String scenarioId, DetailLevel level) {
}
