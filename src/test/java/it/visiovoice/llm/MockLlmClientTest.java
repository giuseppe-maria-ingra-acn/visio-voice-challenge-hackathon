package it.visiovoice.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.visiovoice.model.Provenance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Il client di default deve funzionare senza rete e senza variabili d'ambiente.
 *
 * <p>Non e' un test di comodo: e' il test del requisito "la demo non dipende dalla rete". La
 * rete della sala in cui gira una demo non e' un componente su cui costruire.
 */
class MockLlmClientTest {

    private final MockLlmClient client = new MockLlmClient();

    @Test
    @DisplayName("risponde da fixture senza rete e senza chiavi")
    void rispondeDaFixture() {
        LlmResponse response = client.complete(LlmRequest.of("selftest", "qualunque prompt"));

        assertTrue(response.fromFixture());
        assertFalse(response.isEmpty());
    }

    @Test
    @DisplayName("due chiamate identiche danno lo stesso testo")
    void eDeterministico() {
        LlmRequest request = LlmRequest.of("selftest", "qualunque prompt");

        assertEquals(client.complete(request).text(), client.complete(request).text());
    }

    @Test
    @DisplayName("sostituisce i segnaposto coi valori della richiesta")
    void sostituisceISegnaposto() {
        LlmResponse response = client.complete(
                LlmRequest.of("selftest", "prompt").withVariable("eco", "quarantadue"));

        assertTrue(response.text().contains("quarantadue"));
    }

    @Test
    @DisplayName("la fixture del PNG degli importi esiste: e' il momento centrale della demo")
    void laFixtureDellaTabellaEsiste() {
        LlmResponse response = client.complete(
                LlmRequest.of("descrivi-tabella-importi", "descrivi la tabella"));

        assertTrue(response.fromFixture());
        assertTrue(response.text().contains("203,80"),
                "la descrizione deve riportare i valori dei fatti citati, non valori inventati");
    }

    @Test
    @DisplayName("quando la fixture manca lo dichiara invece di inventare una risposta")
    void fixtureMancanteSiDichiara() {
        LlmResponse response = client.complete(LlmRequest.of("prompt-che-non-esiste", "x"));

        assertFalse(response.fromFixture());
        assertEquals(Provenance.AI_INFERRED, response.claimedProvenance());
        assertTrue(response.text().contains("registrata"),
                "una risposta verosimile al posto di una mancante sarebbe un'allucinazione nostra");
    }
}
