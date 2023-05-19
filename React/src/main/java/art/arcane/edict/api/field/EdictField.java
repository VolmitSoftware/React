package art.arcane.edict.api.field;

import art.arcane.edict.api.endpoint.EdictEndpoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

/**
 * The EdictField class represents a field within an Edict command in the Edict command handling system.
 * Each field has a list of names it can be identified by, a type which defines the kind of data it can hold,
 * and a default value which can be used when no other value is provided.
 * <p>
 * This class is typically used in conjunction with the {@link EdictEndpoint} class to define a command's expected inputs.
 */
@AllArgsConstructor
@Builder
@Data
public class EdictField {

    /**
     * The names by which this field can be identified. The field can be referred to by any of these names.
     */
    @Singular
    private final List<String> names;

    /**
     * The type of data that this field can hold. By default, it is a String.
     */
    @Builder.Default
    private final Class<?> type = String.class;

    /**
     * The default value for this field. This value is used when no other value is provided.
     */
    private final Object defaults;
}
