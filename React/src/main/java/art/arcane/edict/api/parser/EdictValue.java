package art.arcane.edict.api.parser;

import art.arcane.edict.api.Confidence;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Represents a parsed value with an associated confidence level within the Edict command handling system.
 * <br><br>
 * The value is the parsed result produced by an {@link EdictParser}, and confidence represents how certain the parser
 * is that the value was parsed correctly or is appropriate in the context it was used.
 * <br><br>
 * This class is a fundamental part of the Edict command handling system and is used throughout for carrying parsed values
 * along with the confidence of their validity.
 *
 * @param <T> The type of the parsed value.
 */
@Data
@AllArgsConstructor
@Builder
public class EdictValue<T> {
    /**
     * The parsed value of type T.
     * This is the output produced by an EdictParser after parsing an input string.
     */
    private final T value;

    /**
     * The confidence associated with the parsed value.
     * It indicates how certain the parser is that the value was parsed correctly or is appropriate in the context it was used.
     * The confidence is of type Confidence, which is an enum with levels INVALID, LOW, and HIGH.
     */
    private final Confidence confidence;
}
