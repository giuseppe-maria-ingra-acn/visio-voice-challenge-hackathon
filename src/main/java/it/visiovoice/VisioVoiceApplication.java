package it.visiovoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Punto d'ingresso. Tutto il resto e' configurato dal costruttore dei bean, non da qui. */
@SpringBootApplication
public class VisioVoiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VisioVoiceApplication.class, args);
    }
}
