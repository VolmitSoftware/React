/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.edict.api.parser;

import art.arcane.edict.Edict;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Interface that should be implemented by parsers intended to support enum types.
 * <br><br>
 * The implemented parser can process input strings and map them to appropriate enum constants.
 * The parse method is designed to take into account different possible formatting of the input strings
 * (such as lower case, upper case, dashes or underscores), and tries to find the enum constant that
 * best matches the input string.
 * <br><br>
 * Furthermore, the implemented parser is able to suggest possible inputs based on the enum constants,
 * and also to declare whether the input should strictly be one of these suggested inputs.
 *
 * @param <T> The type of the enum that the implemented parser should support.
 */
public interface EnumeratedParser<T extends Enum<T>> extends EdictParser<T>, Suggestive {
    /**
     * Calculates the similarity between the enum constant name and the input string.
     * The similarity is determined by the Levenshtein distance between the two strings.
     *
     * @param enumConstantName the name of the enum constant
     * @param targetString     the input string
     * @return the similarity between the enum constant name and the input string
     */
    private static int calculateSimilarity(String enumConstantName, String targetString) {
        return Edict.calculateLevenshteinDistance(enumConstantName, targetString);
    }

    /**
     * Parses the input string to map it to an appropriate enum constant.
     *
     * @param s the input string to be parsed
     * @return an EdictValue instance which encapsulates the parsed enum constant and an associated confidence level
     */
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
     * Returns the class of the enum that this parser supports.
     *
     * @return the enum class that this parser supports
     */
    Class<? extends Enum<T>> getEnumType();

    /**
     * Returns a list of suggestions for input, based on the enum constants.
     *
     * @return a list of input suggestions
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
     * Returns whether the input should strictly match one of the suggested inputs.
     *
     * @return true if the input should match one of the suggestions, false otherwise
     */
    default boolean isMandatory() {
        return true;
    }
}
