package art.arcane.edict.api.input;

import art.arcane.edict.api.parser.EdictValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Singular;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class MappedEdictInputHolder implements EdictInput,MappedInput {
    private final String key;
    @Singular
    private final List<EdictValue<?>> parsedValues;
}
