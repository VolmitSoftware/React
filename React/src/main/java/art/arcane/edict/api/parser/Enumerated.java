package art.arcane.edict.api.parser;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Implement this to a parser to support an enum
 */
public interface Enumerated<T extends Enum<T>> extends EdictParser<T>, Suggestive {
    @SuppressWarnings("unchecked")
    default EdictValue<T> parse(String s) {
        return Arrays
            .stream(getEnumType().getEnumConstants())
            .min(Comparator.comparingInt(enumConstant -> calculateSimilarity(enumConstant.name(), s.toUpperCase().trim()
                .replaceAll("\\Q-\\E", "_")
                .replaceAll("\\Q \\E", "_")
                .replaceAll("\\Q/\\E", "_")
                .replaceAll("\\Q.\\E", "_"))))
            .map(i -> high((T) i))
            .orElse(low((T) getEnumType().getEnumConstants()[0]));
    }

    /**
     * Get the class of the enum
     * @return the enum class to use
     */
    Class<? extends Enum<T>> getEnumType();

    /**
     * Return a list of suggestions for this input
     * @return the list of suggestions. This can be immutable (List.of() is fine)
     */
    default List<String> getOptions() {
        return Arrays
            .stream(getEnumType().getEnumConstants())
            .map(Enum::name)
            .map(String::toLowerCase)
            .map(i -> i.replaceAll("\\Q_\\E", "-"))
            .toList();
    }

    /**
     * Is this list rigid in the sense that the input must match one of the suggestions? If so, the parser can do a better job at matching
     * to your list of suggestions instead of trying to figure out if the input is a "custom" result or if it "partially" matches one of the
     * suggestions.
     * @return true if the list is rigid, false otherwise
     */
    default boolean isMandatory() {
        return true;
    }

    private static int calculateSimilarity(String enumConstantName, String targetString) {
        return enumConstantName.compareToIgnoreCase(targetString);
    }
}
