package it.visiovoice.orchestrator;

import it.visiovoice.model.FidelityReport;
import it.visiovoice.model.ScriptSegment;
import it.visiovoice.model.SpokenScript;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Gli script prodotti e i loro segmenti, indicizzati per id.
 *
 * <p>Esiste per una funzione precisa: quando l'utente chiede "da dove viene quello che hai
 * appena detto", la risposta deve essere recuperabile a partire dal solo id del segmento.
 * Senza questo indice, quel comando richiederebbe di rigenerare il racconto - e un racconto
 * rigenerato potrebbe non essere identico a quello che l'utente ha sentito, cioe' la risposta
 * riguarderebbe una frase diversa da quella su cui ha chiesto.
 */
@Component
public class ScriptStore {

    private final Map<String, SpokenScript> scripts = new ConcurrentHashMap<>();
    private final Map<String, ScriptSegment> segments = new ConcurrentHashMap<>();
    private final Map<String, FidelityReport> reports = new ConcurrentHashMap<>();

    /** Registra uno script e tutti i suoi segmenti. Da chiamare <b>dopo</b> il gate. */
    public SpokenScript remember(SpokenScript script) {
        scripts.put(script.id(), script);
        script.segments().forEach(segment -> segments.put(segment.id(), segment));
        return script;
    }

    public void rememberReport(FidelityReport report) {
        if (report != null && report.scriptId() != null) {
            reports.put(report.scriptId(), report);
        }
    }

    public Optional<SpokenScript> script(String scriptId) {
        return scriptId == null ? Optional.empty() : Optional.ofNullable(scripts.get(scriptId));
    }

    public Optional<ScriptSegment> segment(String segmentId) {
        return segmentId == null ? Optional.empty() : Optional.ofNullable(segments.get(segmentId));
    }

    public Optional<FidelityReport> report(String scriptId) {
        return scriptId == null ? Optional.empty() : Optional.ofNullable(reports.get(scriptId));
    }

    public int segmentCount() {
        return segments.size();
    }
}
