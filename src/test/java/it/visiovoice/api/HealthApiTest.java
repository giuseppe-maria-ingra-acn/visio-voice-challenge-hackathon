package it.visiovoice.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Il contesto Spring si avvia e l'endpoint di salute risponde.
 *
 * <p>Vale piu' di quanto sembri in questa fase: quattro agenti stanno per aggiungere bean in
 * parallelo, e questo test e' cio' che dice a ognuno se e' stato lui a rompere l'avvio. Verifica
 * anche che l'applicazione parta <b>senza</b> gli strati di dominio, che ancora non esistono.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("l'endpoint di salute risponde e dichiara il client dei modelli attivo")
    void laSaluteRisponde() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.llm").value("mock-fixtures"))
                .andExpect(jsonPath("$.llmFromFixtures").value(true));
    }

    @Test
    @DisplayName("lo scenario reale risulta caricato dal backend")
    void loScenarioRisultaCaricato() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarioIds[0]").value("inps-assegno-unico"))
                .andExpect(jsonPath("$.scenarioLoadErrors").isEmpty());
    }

    @Test
    @DisplayName("una sessione si apre sullo scenario predefinito")
    void laSessioneSiApre() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/session")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists())
                .andExpect(jsonPath("$.scenarioId").value("inps-assegno-unico"))
                .andExpect(jsonPath("$.totalSteps").value(6));
    }
}
