package it.visiovoice.model;

import java.util.List;
import java.util.Objects;

/**
 * Un campo da compilare, come lo conosciamo dopo aver letto la pagina.
 *
 * <p>Il campo che a Marco costa piu' fatica e' quello a cui la pagina <b>non</b> da' un nome.
 * Per quello esiste {@code labelProvenance}: quando l'etichetta sta nel DOM vale
 * {@link Provenance#SOURCE_VERBATIM}, quando invece l'abbiamo ricavata dallo scenario, dal
 * placeholder o dal nome dell'input vale {@link Provenance#AI_INFERRED}, e l'utente lo sente
 * dichiarato. Senza questo campo, dire "questo e' il campo IBAN" di un input senza etichetta
 * sarebbe indistinguibile dal leggere un'etichetta vera, cioe' esattamente la situazione in
 * cui lui non ha modo di verificare.
 *
 * @param id               id del campo nel DOM
 * @param label            il nome del campo in italiano, pronunciabile
 * @param labelProvenance  da dove viene quel nome. Mai {@code null}
 * @param kind             che tipo di dato chiede, per scegliere il validatore
 * @param format           come si compone il valore, in italiano
 * @param example          un esempio pronunciabile, o {@code null}
 * @param required         se senza questo la procedura non avanza
 * @param barrierIds       le barriere che colpiscono <b>questo</b> campo. E' una lista e non un
 *                         singolo riferimento perche' un campo puo' esserne colpito da piu' di
 *                         una: il campo IBAN della replica non ha etichetta <b>e</b> segnala
 *                         l'errore solo col colore. Ripararne una sola lo lascia inutilizzabile
 * @param domSelector      selettore con cui l'estensione ritrova il campo nella pagina
 */
public record FormField(
        String id,
        String label,
        Provenance labelProvenance,
        FieldKind kind,
        String format,
        String example,
        boolean required,
        List<String> barrierIds,
        String domSelector) {

    public FormField {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(labelProvenance, "labelProvenance");
        kind = kind == null ? FieldKind.OTHER : kind;
        barrierIds = List.copyOf(barrierIds == null ? List.of() : barrierIds);
        domSelector = domSelector == null ? "#" + id : domSelector;
    }

    /** Campo la cui etichetta e' stata letta nel DOM: il caso sano. */
    public static FormField labelled(String id, String label, FieldKind kind, String format,
                                     String example, boolean required) {
        return new FormField(id, label, Provenance.SOURCE_VERBATIM, kind, format, example,
                required, List.of(), null);
    }

    /**
     * Campo a cui la pagina non da' un nome e a cui il nome lo diamo noi. La provenance
     * dedotta non e' un dettaglio contabile: e' cio' che l'utente sentira' prima di fidarsi.
     */
    public static FormField inferredLabel(String id, String label, FieldKind kind, String format,
                                          String example, boolean required, List<String> barrierIds) {
        return new FormField(id, label, Provenance.AI_INFERRED, kind, format, example,
                required, barrierIds, null);
    }

    /** True se la pagina non da' un nome accessibile a questo campo. */
    public boolean hasInferredLabel() {
        return labelProvenance == Provenance.AI_INFERRED;
    }

    /** Copia con altre barriere: serve a chi incrocia i campi con le barriere rilevate. */
    public FormField withBarrierIds(List<String> ids) {
        return new FormField(id, label, labelProvenance, kind, format, example, required, ids, domSelector);
    }
}
