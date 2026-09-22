package it.visiovoice.llm;

import it.visiovoice.model.Provenance;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Il client attivo per default: risponde da file su disco, senza rete e senza chiavi.
 *
 * <p>E' il bean primario e non un ripiego. Due conseguenze volute: la demo funziona in una sala
 * senza rete, e i test sono riproducibili. Un client dal vivo si aggiunge come bean non
 * primario quando esiste una chiave, e il resto del sistema non cambia di una riga.
 *
 * <p>Deterministico per costruzione: stesso {@code promptId}, stesso file, stessa risposta.
 * Nessun numero casuale, nessun orologio dentro il testo, nessuna dipendenza dall'ambiente. Se
 * la fixture manca, la risposta lo <b>dichiara</b> ed e' marcata come dedotta: una risposta
 * inventata al posto di una mancante sarebbe la stessa allucinazione che il progetto esiste per
 * evitare, solo prodotta da noi invece che dal modello.
 */
@Component
@Primary
public class MockLlmClient implements LlmClient {

    /** Dove stanno le risposte registrate. Le riempie {@code narration-engineer}. */
    public static final String FIXTURE_DIR = "fixtures/";

    /** Provata in ordine: la prima che esiste vince. */
    private static final List<String> EXTENSIONS = List.of(".txt", ".json", ".md");

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "mock-fixtures";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        String promptId = request.promptId();
        String body = cache.computeIfAbsent(promptId, MockLlmClient::readFixture);
        if (body.isEmpty()) {
            return new LlmResponse(missingFixtureText(promptId), name(), false,
                    Provenance.AI_INFERRED, List.of(), 0L);
        }
        return new LlmResponse(render(body, request.variables()), name(), true,
                Provenance.AI_REPHRASED, List.of(), 0L);
    }

    /** True se esiste una fixture per questo prompt. Usato da {@code /api/health}. */
    public boolean hasFixture(String promptId) {
        return !cache.computeIfAbsent(promptId, MockLlmClient::readFixture).isEmpty();
    }

    private static String readFixture(String promptId) {
        for (String extension : EXTENSIONS) {
            ClassPathResource resource = new ClassPathResource(FIXTURE_DIR + promptId + extension);
            if (!resource.exists()) {
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                // Un file illeggibile si tratta come un file assente: qui non c'e' nulla che
                // l'utente possa fare, e propagare l'eccezione spegnerebbe la demo per un
                // problema di permessi su un file di prova.
                return "";
            }
        }
        return "";
    }

    /** Sostituisce i segnaposto {{nome}} coi valori della richiesta. */
    private static String render(String body, Map<String, String> variables) {
        String out = body;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            out = out.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return out;
    }

    private static String missingFixtureText(String promptId) {
        return "Non ho una risposta registrata per questa richiesta (" + promptId
                + "). Non posso raccontarti questa parte: conviene chiedere aiuto a una persona.";
    }
}
