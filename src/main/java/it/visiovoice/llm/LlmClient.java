package it.visiovoice.llm;

/**
 * Il confine dietro cui sta un modello generativo.
 *
 * <p>Esiste per una ragione operativa e una di disegno. Operativa: la demo non deve dipendere
 * dalla rete della sala in cui gira, e un test che chiama un modello generativo non verifica il
 * nostro codice, verifica il modello. Di disegno: tenere il modello dietro un'interfaccia rende
 * visibile <b>quanto poco</b> del sistema ne ha bisogno, che e' la proprieta' su cui si regge
 * la verificabilita' di tutto il resto.
 *
 * <p>Un'implementazione non lancia eccezioni verso l'alto per un problema di rete o di chiave:
 * restituisce una risposta vuota. Chi chiama deve poter degradare in silenzio, non spegnersi.
 */
public interface LlmClient {

    /** Nome dell'implementazione attiva, mostrato in {@code /api/health}. */
    String name();

    /** True se questa implementazione e' in grado di rispondere adesso. */
    boolean available();

    /** Chiede, e riceve. Mai {@code null}: al peggio una risposta vuota. */
    LlmResponse complete(LlmRequest request);
}
