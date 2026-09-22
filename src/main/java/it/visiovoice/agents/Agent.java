package it.visiovoice.agents;

/**
 * Un agente: una trasformazione con un nome, da un input a un output.
 *
 * <p>Firma unica per tutti. Il guadagno e' nell'orchestratore: con una firma sola resta un
 * ciclo che traccia e invoca, invece di uno switch che cresce di un ramo per ogni agente
 * nuovo. Ed e' cio' che permette di misurare i tempi e registrare i passaggi in un punto solo:
 * la mappa del contributo AI si genera da quei passaggi, non si scrive a mano.
 *
 * @param <I> cosa riceve
 * @param <O> cosa produce
 */
public interface Agent<I, O> {

    /** Nome stabile, quello che comparira' nei trace e nella mappa del contributo AI. */
    String name();

    /**
     * Fa il lavoro.
     *
     * <p>Non lancia eccezioni per cio' che l'utente puo' sbagliare: quelle diventano frasi che
     * una sintesi vocale legge. Gli errori di dominio sono valori di ritorno.
     */
    O run(I input, AgentContext ctx);
}
