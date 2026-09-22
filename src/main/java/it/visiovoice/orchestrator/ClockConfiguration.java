package it.visiovoice.orchestrator;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * L'orologio come dipendenza e non come chiamata statica.
 *
 * <p>Una riga che compra due cose: i test non dipendono dall'ora in cui girano, e non compare
 * un {@code Instant.now()} sparso in cinque classi diverse - che e' il modo in cui un test
 * comincia a fallire solo a mezzanotte.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    @Scope("singleton")
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
