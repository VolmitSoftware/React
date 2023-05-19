package art.arcane.edict.api.input;

import art.arcane.edict.api.parser.EdictValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

/**
 * The EdictInputHolder class is an implementation of the EdictInput interface. It provides a concrete representation
 * for an input to an Edict command in the Edict command handling system.
 * <br><br>
 * This class holds a list of parsed values which represent the interpreted values of the input. These parsed values
 * each have an associated level of confidence about their validity.
 */
@Builder
@Data
@AllArgsConstructor
public class EdictInputHolder implements EdictInput {
    /**
     * The list of parsed values that this input holds.
     */
    @Singular
    private final List<EdictValue<?>> parsedValues;
}
