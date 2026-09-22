package it.visiovoice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Un campo come lo <b>dichiara lo scenario</b>, cioe' come lo ha documentato una persona che ha
 * guardato il servizio.
 *
 * <p>Perche' non e' direttamente un {@link FormField}: in {@code FormField} la provenance
 * dell'etichetta e' obbligatoria, e uno scenario in JSON non puo' dichiararla per tutti i suoi
 * quindici campi senza diventare illeggibile. Se accettassimo un default silenzioso qui,
 * l'etichetta "IBAN" - che sulla pagina <b>non esiste</b> - arriverebbe all'utente marcata
 * come letta dalla pagina. Passare da {@link #asFormField(Provenance)} costringe chi converte
 * a dire da dove viene quell'etichetta, che e' l'unica domanda che conta.
 *
 * @param barrier  forma breve, un solo id di barriera. Resta per compatibilita' con gli
 *                 scenari gia' scritti
 * @param barriers forma completa: un campo puo' essere colpito da piu' barriere insieme. Se
 *                 entrambi i campi sono presenti, {@link #barrierIds()} li unisce
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScenarioField(
        String id,
        String label,
        String format,
        String example,
        boolean required,
        String barrier,
        List<String> barriers,
        String accessibleNote) {

    public ScenarioField {
        Objects.requireNonNull(id, "id");
        barriers = List.copyOf(barriers == null ? List.of() : barriers);
    }

    /**
     * Tutte le barriere che colpiscono questo campo, unione delle due forme e senza duplicati.
     * Chi consuma lo scenario usa solo questo metodo e non i due campi.
     */
    public List<String> barrierIds() {
        List<String> all = new ArrayList<>();
        if (barrier != null && !barrier.isBlank()) {
            all.add(barrier);
        }
        barriers.stream().filter(id -> !all.contains(id)).forEach(all::add);
        return List.copyOf(all);
    }

    /**
     * Converte in campo di dominio. La provenance dell'etichetta e' un parametro obbligatorio,
     * non un default: chi chiama deve aver deciso se quell'etichetta sta nella pagina.
     */
    public FormField asFormField(Provenance labelProvenance) {
        return new FormField(id, label, labelProvenance, guessKind(), format, example,
                required, barrierIds(), null);
    }

    /**
     * Tipo di dato indovinato dall'id e dal formato dichiarato. E' un'euristica dichiarata:
     * serve a scegliere un validatore, non a produrre testo per l'utente, quindi sbagliarla
     * costa un controllo in meno e non una frase falsa.
     */
    public FieldKind guessKind() {
        String key = (id + " " + (format == null ? "" : format)).toLowerCase();
        if (key.contains("iban")) return FieldKind.IBAN;
        if (key.contains("_cf") || key.contains("codice fiscale") || key.startsWith("cf")) return FieldKind.FISCAL_CODE;
        if (key.contains("nascita") || key.contains("data") || key.contains("gg/mm")) return FieldKind.DATE;
        if (key.contains("isee") || key.contains("importo") || key.contains("euro")) return FieldKind.CURRENCY;
        return FieldKind.TEXT;
    }
}
