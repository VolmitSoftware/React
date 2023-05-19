package art.arcane.edict.api.input;

import art.arcane.edict.api.parser.EdictValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Singular;

import java.util.List;

/**
 * The MappedEdictInputHolder class implements both the EdictInput and MappedInput interfaces,
 * representing a type of EdictInput that is mapped to a key in the Edict command handling system.
 * <p>
 * Each instance of this class holds the key to which the input is mapped and a list of parsed
 * values, which are the results of parsing the input string with respect to the command's requirements.
 */
@Data
@AllArgsConstructor
@Builder
public class MappedEdictInputHolder implements EdictInput,MappedInput {
    private final String key;
    @Singular
    private final List<EdictValue<?>> parsedValues;
}
