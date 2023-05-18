package art.arcane.edict.api.parser;

import java.util.List;

/**
 * Implement this to a parser to allow for suggestions. You can also use this to force an input to match to one of the suggested parsers.
 */
public interface Suggestive {
    /**
     * Return a list of suggestions for this input
     * @return the list of suggestions. This can be immutable (List.of() is fine)
     */
    List<String> getOptions();

    /**
     * Is this list rigid in the sense that the input must match one of the suggestions? If so, the parser can do a better job at matching
     * to your list of suggestions instead of trying to figure out if the input is a "custom" result or if it "partially" matches one of the
     * suggestions.
     * @return true if the list is rigid, false otherwise
     */
    boolean isMandatory();
}
