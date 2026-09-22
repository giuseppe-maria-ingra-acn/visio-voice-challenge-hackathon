package it.visiovoice.model;

import java.util.Objects;

/**
 * Cio' che il gate restituisce: lo script <b>corretto</b> e il verbale del controllo.
 *
 * <p>Due valori e non uno perche' il gate non si limita a giudicare, interviene: un segmento
 * che chiedeva di essere considerato riformulato e non supera l'ancoraggio esce da qui
 * retrocesso. Restituire solo il rapporto lascerebbe a chi chiama la responsabilita' di
 * applicare la retrocessione, e quella e' la riga di codice che prima o poi qualcuno
 * dimentica.
 */
public record FidelityVerdict(SpokenScript script, FidelityReport report) {

    public FidelityVerdict {
        Objects.requireNonNull(script, "script");
        Objects.requireNonNull(report, "report");
    }

    /** True se nessun segmento e' stato toccato. */
    public boolean isClean() {
        return report.isClean();
    }
}
