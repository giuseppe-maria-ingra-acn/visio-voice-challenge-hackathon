package it.visiovoice.procedure;

import it.visiovoice.agents.ProcedurePort;
import it.visiovoice.model.DetailLevel;
import it.visiovoice.model.FieldGuidance;
import it.visiovoice.model.FieldKind;
import it.visiovoice.model.FormField;
import it.visiovoice.model.ProcedurePlan;
import it.visiovoice.model.ProcedureStep;
import it.visiovoice.model.Provenance;
import it.visiovoice.model.Scenario;
import it.visiovoice.model.ScenarioStep;
import it.visiovoice.model.ScreenModel;
import it.visiovoice.model.ScriptSegment;
import it.visiovoice.model.SegmentRole;
import it.visiovoice.model.SpokenScript;
import it.visiovoice.orchestrator.SessionState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Il canale che accompagna: traduce lo scenario in un piano eseguibile, guida campo per campo,
 * e compone il riepilogo parlato prima dell'invio.
 *
 * <p>Piano: ogni {@link ScenarioStep} diventa un {@link ProcedureStep} con i campi convertiti
 * in {@link FormField} con la provenance dell'etichetta dichiarata esplicitamente.
 *
 * <p>Coaching: quattro elementi in ordine — cosa chiede, il formato, un esempio, se obbligatorio.
 *
 * <p>Riepilogo: legge i valori dal DOM (parametro), raggruppati per passo, numeri scanditi a
 * gruppi, campi vuoti segnalati. Marco non vede il campo accanto al valore: il riepilogo deve
 * bastare da solo.
 */
@Service
public class ProcedureAgent implements ProcedurePort {

    @Override
    public ProcedurePlan plan(ScreenModel screen, Scenario scenario) {
        Objects.requireNonNull(scenario, "scenario");

        List<ProcedureStep> steps = new ArrayList<>();
        for (ScenarioStep scenarioStep : scenario.procedure()) {
            steps.add(buildStep(scenarioStep, scenario));
        }

        return new ProcedurePlan(scenario.id(), scenario.goal(), steps);
    }

    private ProcedureStep buildStep(ScenarioStep scenarioStep, Scenario scenario) {
        List<FormField> fields = new ArrayList<>();
        for (var sf : scenarioStep.fields()) {
            // La provenance dell'etichetta: SOURCE_VERBATIM se il campo ha un label nella pagina,
            // AI_INFERRED se la pagina non lo nomina (come il campo IBAN).
            // La barriera b2 (UNLABELED_INPUT) segnala che la pagina non ha un label accessibile.
            Provenance labelProvenance = sf.barrierIds().contains("b2")
                    ? Provenance.AI_INFERRED
                    : Provenance.SOURCE_VERBATIM;
            fields.add(sf.asFormField(labelProvenance));
        }

        // Il criterio di completamento: usa quello dello scenario se dichiarato,
        // altrimenti lo deduce dai campi obbligatori.
        String criterion = scenarioStep.completionCriterion();
        if (criterion == null || criterion.isBlank()) {
            criterion = buildCriterion(scenarioStep);
        }

        // La provenance del passo: SOURCE_VERBATIM se il titolo viene dallo scenario JSON
        // (che a sua volta documenta il servizio reale). I passi dello scenario sono dati
        // documentali, non generati a runtime.
        return new ProcedureStep(
                scenarioStep.step(),
                scenarioStep.title(),
                scenarioStep.requiredData(),
                fields,
                criterion,
                Provenance.SOURCE_VERBATIM);
    }

    private String buildCriterion(ScenarioStep step) {
        List<String> required = step.fields().stream()
                .filter(f -> f.required())
                .map(f -> f.label())
                .toList();
        if (required.isEmpty()) {
            return "Il passo e' completo quando hai ascoltato il contenuto e scelto di procedere.";
        }
        if (required.size() == 1) {
            return "Il passo e' completo quando hai compilato il campo obbligatorio: " + required.get(0) + ".";
        }
        return "Il passo e' completo quando hai compilato " + required.size()
                + " campi obbligatori: " + String.join(", ", required) + ".";
    }

    @Override
    public FieldGuidance coach(FormField field, SessionState state) {
        Objects.requireNonNull(field, "field");

        // 1. Cosa chiede il campo — dalla label se c'e', dichiarando se e' dedotta
        String spokenLabel;
        Provenance provenance;
        if (field.label() != null && !field.label().isBlank()) {
            spokenLabel = field.label();
            provenance = field.labelProvenance();
        } else {
            spokenLabel = "Campo " + field.id();
            provenance = Provenance.AI_INFERRED;
        }

        // 2. Il formato
        String instruction = buildInstruction(field);

        // 3. Un esempio — dallo scenario se presente nel campo
        String example = field.example();

        // 4. Se e' obbligatorio e cosa succede se lo si salta
        if (field.required()) {
            String requiredNote = " Questo campo e' obbligatorio: senza di esso la domanda "
                    + "non puo' essere inviata.";
            instruction = instruction + requiredNote;
        }

        return new FieldGuidance(
                field.id(),
                spokenLabel,
                instruction,
                example,
                provenance,
                List.of(),
                field.barrierIds());
    }

    private String buildInstruction(FormField field) {
        // Istruzione specifica per tipo di campo
        if (field.kind() == FieldKind.IBAN) {
            return "Inserisci il codice IBAN del conto corrente. "
                    + "Ha 27 caratteri: inizia con IT, seguito da due cifre di controllo, "
                    + "poi il codice ABI, il CAB e il numero di conto. "
                    + "Attenzione: la pagina non annuncia questo campo con una label, "
                    + "e gli errori vengono segnalati solo con un bordo rosso che non senti: "
                    + "usa il controllo integrato di VisioVoice prima di procedere.";
        }
        if (field.kind() == FieldKind.FISCAL_CODE) {
            return "Inserisci il codice fiscale di 16 caratteri. "
                    + "Lo trovi sulla tessera sanitaria o sul documento d'identita'.";
        }
        if (field.kind() == FieldKind.DATE) {
            return "Inserisci la data nel formato giorno/mese/anno, "
                    + "con due cifre per il giorno e il mese e quattro per l'anno.";
        }
        if (field.kind() == FieldKind.CURRENCY) {
            return "Inserisci l'importo in euro. "
                    + "Usa la virgola come separatore decimale e il punto come separatore delle migliaia.";
        }

        // Istruzione generica: usa il format dal campo se disponibile
        if (field.format() != null && !field.format().isBlank()) {
            return "Formato atteso: " + field.format() + ".";
        }
        return "";
    }

    @Override
    public SpokenScript reviewBeforeSubmit(ProcedurePlan plan, Map<String, String> currentValues) {
        Objects.requireNonNull(plan, "plan");
        Map<String, String> values = currentValues != null ? currentValues : Map.of();

        List<ScriptSegment> segments = new ArrayList<>();
        String scriptId = "review-" + plan.scenarioId();

        // Intestazione del riepilogo
        segments.add(ScriptSegment.verbatim(
                scriptId + "-intro",
                SegmentRole.REVIEW,
                "Riepilogo di cio' che stai per inviare. Ascolta con attenzione: "
                + "dopo l'invio non sara' possibile modificare i dati.",
                List.of()));

        // Ogni passo con i suoi campi
        for (ProcedureStep step : plan.steps()) {
            if (step.fields().isEmpty()) {
                continue; // Passi senza campi (passo 0, riepilogo) non hanno dati da riepilogare
            }

            String stepSegId = scriptId + "-step-" + step.index();
            segments.add(ScriptSegment.verbatim(
                    stepSegId,
                    SegmentRole.STEP_STATUS,
                    "Passo " + step.index() + ": " + step.title() + ".",
                    List.of()));

            for (FormField field : step.fields()) {
                String value = values.get(field.id());
                String fieldSegId = scriptId + "-field-" + field.id();
                String fieldLabel = field.label() != null ? field.label() : field.id();

                if (value == null || value.isBlank()) {
                    String emptyMsg = field.required()
                            ? fieldLabel + ": NON COMPILATO. Questo campo e' obbligatorio — "
                              + "torna indietro e compilalo prima di inviare la domanda."
                            : fieldLabel + ": non compilato.";
                    segments.add(ScriptSegment.verbatim(fieldSegId, SegmentRole.DATA, emptyMsg, List.of()));
                } else {
                    String spokenValue = spokenValueFor(field, value.trim());
                    segments.add(ScriptSegment.verbatim(
                            fieldSegId,
                            SegmentRole.DATA,
                            fieldLabel + ": " + spokenValue + ".",
                            List.of()));
                }
            }
        }

        // Chiede conferma esplicita
        segments.add(ScriptSegment.verbatim(
                scriptId + "-confirm",
                SegmentRole.CONFIRMATION,
                "Hai ascoltato tutti i dati. Confermi l'invio della domanda? "
                + "Rispondi si' per inviare o no per tornare a modificare.",
                List.of()));

        return SpokenScript.of(scriptId, plan.scenarioId(), DetailLevel.FULL, segments);
    }

    /**
     * Formatta un valore per la pronuncia: gli IBAN e i codici lunghi vengono scanditi
     * a gruppi di 4 caratteri per facilitare la verifica uditiva.
     */
    private String spokenValueFor(FormField field, String value) {
        if (field.kind() == FieldKind.IBAN || field.kind() == FieldKind.FISCAL_CODE) {
            return spokenInGroups(value, 4);
        }
        return value;
    }

    /** Scandisce una stringa a gruppi di {@code groupSize} caratteri separati da spazio. */
    private String spokenInGroups(String value, int groupSize) {
        String clean = value.replaceAll("\\s+", "").toUpperCase();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clean.length(); i++) {
            if (i > 0 && i % groupSize == 0) {
                sb.append(' ');
            }
            sb.append(clean.charAt(i));
        }
        return sb.toString();
    }
}
