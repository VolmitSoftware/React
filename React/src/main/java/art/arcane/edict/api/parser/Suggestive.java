package art.arcane.edict.api.parser;

import java.util.List;

/**
 * Interface that should be implemented by parsers designed to support suggestion functionality.
 * <p>
 * The implemented parser can generate a list of possible options or suggestions for the input.
 * Additionally, it can specify whether the input must strictly match one of these suggested options,
 * or whether partial or custom matches are allowed. This can help in guiding the user input
 * or increasing the accuracy of the parser.
 *
 */
public interface Suggestive {

    /**
     * Provides a list of options or suggestions for the input.
     *
     * @return A list of suggestions. This list can be immutable (List.of() is fine).
     */
    List<String> getOptions();

    /**
     * Specifies whether the input must strictly match one of the options provided by getOptions().
     * If true, the parser can focus on matching the input to one of the suggested options instead of
     * trying to parse it as a custom or partially matching input.
     *
     * @return True if the list of options is rigid and the input must match one of the options.
     *         False if custom or partial matches are allowed.
     */
    boolean isMandatory();
}
