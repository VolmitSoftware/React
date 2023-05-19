package art.arcane.edict.api.input;

import art.arcane.edict.api.parser.EdictValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

/**
 * The PositionalEdictInputHolder class implements both the PositionalInput and EdictInput interfaces,
 * representing a type of EdictInput identified by its position in the Edict command handling system.
 * <p>
 * Each instance of this class holds both the actual position of the input in the command string (real position)
 * and its ordinal position amongst positional inputs (position). It also stores a list of parsed values
 * that are the result of parsing the input string as per the requirements of the command.
 */
@Data
@Builder
@AllArgsConstructor
public class PositionalEdictInputHolder implements PositionalInput, EdictInput  {
    private final int position;
    private final int realPosition;
    @Singular
    private final List<EdictValue<?>> parsedValues;
}
