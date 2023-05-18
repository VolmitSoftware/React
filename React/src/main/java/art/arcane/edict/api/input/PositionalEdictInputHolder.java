package art.arcane.edict.api.input;

import art.arcane.edict.api.parser.EdictValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Singular;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class PositionalEdictInputHolder implements PositionalInput, EdictInput  {
    private final int position;
    private final int realPosition;
    @Singular
    private final List<EdictValue<?>> parsedValues;
}
