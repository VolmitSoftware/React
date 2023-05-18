package art.arcane.edict.api.input;

import art.arcane.edict.api.parser.EdictValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

@Builder
@Data
public class EdictInputHolder implements EdictInput {
    @Singular
    private final List<EdictValue<?>> parsedValues;

    public EdictInputHolder() {
        this.parsedValues = List.of();
    }

    public EdictInputHolder(List<EdictValue<?>> parsedValues) {
        this.parsedValues = parsedValues;
    }
}
