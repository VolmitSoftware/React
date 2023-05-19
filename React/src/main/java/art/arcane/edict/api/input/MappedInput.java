package art.arcane.edict.api.input;

/**
 * The MappedInput interface represents a specialized form of an EdictInput in the Edict command handling system
 * where the input is mapped to a key. This mapping can be useful in situations where inputs are identified
 * or categorized by certain keys.
 * <p>
 * The interface provides a method to retrieve the key to which the input is mapped.
 */
public interface MappedInput {
    /**
     * The key this input is mapped to
     * @return the key
     */
    String getKey();
}
