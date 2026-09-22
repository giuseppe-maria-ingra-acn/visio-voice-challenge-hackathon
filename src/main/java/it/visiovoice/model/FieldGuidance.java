package it.visiovoice.model;

import java.util.List;
import java.util.Objects;

/**
 * Cosa dire all'utente quando arriva su un campo.
 *
 * <p>Il caso che questo tipo esiste per risolvere: un input senza etichetta, di cui lo screen
 * reader annuncia soltanto "modifica, vuoto". {@code spokenLabel} da' un nome a quel campo e
 * {@code provenance} dice se quel nome viene dalla pagina o da noi. Le due cose vanno insieme:
 * dare un nome senza dichiararne l'origine e' indistinguibile, per chi ascolta, dal leggere
 * un'etichetta vera.
 *
 * @param spokenLabel come si chiama questo campo, pronunciabile
 * @param instruction cosa scriverci e come si compone
 * @param example     un valore di esempio. Va scelto verosimile ma riconoscibile come esempio:
 *                    un esempio che sembra un dato vero confonde chi non vede la pagina
 * @param barrierIds  le barriere che questa guida sta riparando. Lista, perche' su un campo
 *                    possono esserci due problemi diversi da annunciare insieme
 */
public record FieldGuidance(
        String fieldId,
        String spokenLabel,
        String instruction,
        String example,
        Provenance provenance,
        List<String> sourceRefs,
        List<String> barrierIds) {

    public FieldGuidance {
        Objects.requireNonNull(fieldId, "fieldId");
        Objects.requireNonNull(spokenLabel, "spokenLabel");
        Objects.requireNonNull(provenance, "provenance");
        instruction = instruction == null ? "" : instruction;
        sourceRefs = List.copyOf(sourceRefs == null ? List.of() : sourceRefs);
        barrierIds = List.copyOf(barrierIds == null ? List.of() : barrierIds);
    }

    /**
     * Il testo completo, avvertenza compresa e nell'ordine in cui si pronuncia: prima come si
     * chiama, poi cosa scriverci, poi l'esempio.
     */
    public String spokenText() {
        StringBuilder sb = new StringBuilder();
        String disclaimer = provenance.spokenDisclaimer();
        if (!disclaimer.isEmpty()) {
            sb.append(disclaimer).append(' ');
        }
        sb.append(spokenLabel).append('.');
        if (!instruction.isBlank()) {
            sb.append(' ').append(instruction);
        }
        if (example != null && !example.isBlank()) {
            sb.append(" Per esempio: ").append(example).append('.');
        }
        return sb.toString();
    }

    /** La guida come segmento, per entrare in uno {@link SpokenScript} e nel gate con tutti. */
    public ScriptSegment asSegment(String segmentId) {
        return new ScriptSegment(segmentId, SegmentRole.FIELD_PROMPT, spokenText(), provenance, sourceRefs);
    }
}
