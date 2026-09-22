package it.visiovoice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;

/**
 * Una cosa che nella pagina non funziona per chi non vede, descritta abbastanza bene da poterla
 * riparare e da poterla raccontare.
 *
 * <p>Lo stesso tipo serve due strade: le barriere dichiarate nello scenario (scritte a mano,
 * con la loro citazione WCAG) e quelle rilevate da {@code perception} sull'HTML. Un tipo solo
 * perche' la riparazione a valle non deve sapere da dove arriva l'informazione, e perche' la
 * demo confronta le due liste: cio' che lo scenario dichiara e cio' che il rilevatore trova.
 *
 * @param id                riferimento stabile, citato dai campi che la barriera colpisce
 * @param type              famiglia di barriera
 * @param location          dove sta, in termini che un essere umano puo' verificare
 * @param screenReaderHears cio' che l'utente sente <b>oggi</b>, prima di noi. Vuoto quando la
 *                          barriera consiste esattamente nel non sentire nulla, che e' il caso
 *                          peggiore: l'utente non ha alcun segnale di cui insospettirsi
 * @param whyBlocking       perche' questo blocca <b>questa</b> persona in <b>questo</b> passo
 * @param source            la citazione: criterio WCAG, monitoraggio, evidenza
 * @param fidelity          se questa barriera e' stata osservata o ricostruita. Sta sulla
 *                          singola barriera e non solo sul servizio perche' in un solo
 *                          scenario le due cose convivono, e perche' la demo deve poter dire
 *                          "questa l'abbiamo vista" senza rivendicarlo per tutte
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Barrier(
        String id,
        BarrierType type,
        String location,
        String screenReaderHears,
        String whyBlocking,
        String source,
        EvidenceFidelity fidelity) {

    public Barrier {
        Objects.requireNonNull(id, "id");
        type = type == null ? BarrierType.UNKNOWN : type;
        screenReaderHears = screenReaderHears == null ? "" : screenReaderHears;
    }

    /** Barriera rilevata dal codice sull'HTML: osservata per definizione, la fonte e' il DOM. */
    public static Barrier detected(String id, BarrierType type, String location,
                                   String screenReaderHears, String whyBlocking) {
        return new Barrier(id, type, location, screenReaderHears, whyBlocking,
                "rilevata da perception sul DOM della pagina", EvidenceFidelity.OSSERVATO);
    }

    /** Se true, l'utente oggi non riceve alcun segnale: la barriera e' silenziosa. */
    public boolean isSilent() {
        return screenReaderHears.isBlank();
    }
}
