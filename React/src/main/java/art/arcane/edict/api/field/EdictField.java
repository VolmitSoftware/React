package art.arcane.edict.api.field;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

/**
 * Represents a field in a command that has a type it's looking for it also has a name to map to and a position to fill for
 */
@AllArgsConstructor
@Builder
@Data
public class EdictField {
    @Singular
    private final List<String> names;
    @Builder.Default
    private final Class<?> type = String.class;
    private final Object defaults;
}
