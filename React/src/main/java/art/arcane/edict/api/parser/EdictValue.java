package art.arcane.edict.api.parser;

import art.arcane.edict.api.Confidence;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class EdictValue<T> {
    private final T value;
    private final Confidence confidence;
}
