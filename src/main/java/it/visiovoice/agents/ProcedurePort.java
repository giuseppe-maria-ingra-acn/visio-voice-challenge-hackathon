package it.visiovoice.agents;

import it.visiovoice.model.FieldGuidance;
import it.visiovoice.model.FormField;
import it.visiovoice.model.ProcedurePlan;
import it.visiovoice.model.Scenario;
import it.visiovoice.model.ScreenModel;
import it.visiovoice.model.SpokenScript;
import it.visiovoice.orchestrator.SessionState;
import java.util.Map;

/**
 * Il canale che accompagna: piano dei passi, guida campo per campo, riepilogo.
 *
 * <p>Risponde alla domanda "cosa devo fare adesso", che e' diversa da "cosa c'e' qui" e arriva
 * in un altro momento. Per questo e' un canale separato dalla narrazione e non un suo livello
 * di dettaglio.
 */
public interface ProcedurePort {

    /** Il piano: i passi, cosa serve avere, come si capisce che un passo e' chiuso. */
    ProcedurePlan plan(ScreenModel screen, Scenario scenario);

    /** Cosa dire su un campo appena l'utente ci arriva. */
    FieldGuidance coach(FormField field, SessionState state);

    /**
     * Il riepilogo parlato prima dell'invio.
     *
     * <p>I valori arrivano come parametro perche' sono stati <b>letti dal DOM</b> in questo
     * istante, non ricavati dallo stato di sessione. La pagina e' la fonte di verita': se
     * rileggessimo una nostra copia, un riepilogo corretto potrebbe descrivere un form diverso
     * da quello che l'utente sta per inviare, e lui non avrebbe modo di accorgersene.
     *
     * @param currentValues id del campo verso il valore attualmente nel campo
     */
    SpokenScript reviewBeforeSubmit(ProcedurePlan plan, Map<String, String> currentValues);
}
