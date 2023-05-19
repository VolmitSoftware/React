package art.arcane.edict.api.request;

import art.arcane.edict.api.Confidence;
import art.arcane.edict.api.field.EdictField;
import art.arcane.edict.api.input.EdictInput;
import lombok.Builder;
import lombok.Data;

/**
 * The `EdictFieldResponse` class represents the result of parsing a specific field in an Edict.
 * Each instance of this class corresponds to a single parsed field, and contains data about the parsing results,
 * such as the associated EdictField and EdictInput, the parsed value, and information about the confidence and position of the parsing.
 *
 * @see EdictField
 * @see EdictInput
 * @see Confidence
 */
@Data
@Builder
public class EdictFieldResponse {

    /**
     * The `EdictField` that was parsed to generate this response.
     */
    private EdictField field;

    /**
     * The `EdictInput` that was used to generate this response.
     */
    private EdictInput input;

    /**
     * The actual value that was parsed from the `EdictInput`.
     */
    private Object value;

    /**
     * The confidence level of the parsed value.
     * This indicates how confident the parser is that the parsed value correctly represents the input.
     */
    private Confidence confidence;

    /**
     * The position of the input that was parsed to generate this response.
     * This is the index of the input in the original list of inputs to the `Edict`.
     */
    private int inputPosition;

    /**
     * The difference between the expected position of the field's name and the actual position in the input.
     * This value can be used to measure the 'drift' of the field in the input,
     * or how far away the field's name is from where it was expected to be.
     */
    private int nameDrift;
}
