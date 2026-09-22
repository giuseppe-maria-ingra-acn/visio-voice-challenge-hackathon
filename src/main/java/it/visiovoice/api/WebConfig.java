package it.visiovoice.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Fa in modo che i percorsi di cartella servano il loro {@code index.html}.
 *
 * <p>Spring Boot serve automaticamente {@code index.html} solo per la radice: per una
 * sottocartella come {@code /demo/} risponde 404. E' un dettaglio che si scopre tardi e
 * nel momento peggiore, perche' {@code localhost:8080/demo/} e' esattamente quello che una
 * persona digita, ed e' il percorso scritto in tutta la documentazione del progetto.
 *
 * <p>Sono mappati sia {@code /demo} che {@code /demo/}: chi digita a mano omette la barra
 * finale piu' spesso di quanto la scriva.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/demo").setViewName("forward:/demo/index.html");
        registry.addViewController("/demo/").setViewName("forward:/demo/index.html");
        registry.addViewController("/fallback").setViewName("forward:/fallback/index.html");
        registry.addViewController("/fallback/").setViewName("forward:/fallback/index.html");
    }
}
