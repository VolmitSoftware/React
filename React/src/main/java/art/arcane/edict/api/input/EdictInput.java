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

package art.arcane.edict.api.input;

import art.arcane.edict.api.Confidence;
import art.arcane.edict.api.parser.EdictValue;

import java.util.List;
import java.util.Optional;

/**
 * The EdictInput interface represents an input provided to an Edict command in the Edict command handling system.
 * The input can hold multiple parsed values, each with an associated level of confidence about its validity.
 * <br><br>
 * The interface provides methods to retrieve these parsed values either in their entirety or with a minimum
 * confidence level, and to convert the input into a specified type, given a minimum confidence level.
 */
public interface EdictInput {
    /**
     * Retrieves all parsed values from the input.
     *
     * @return A list of all parsed values.
     */
    List<EdictValue<?>> getParsedValues();

    /**
     * Retrieves all parsed values with a minimum confidence level.
     *
     * @param minimumConfidence The minimum confidence level.
     * @return A list of all parsed values with at least the specified confidence level.
     */
    default List<EdictValue<?>> getParsedValues(Confidence minimumConfidence) {
        return getParsedValues().stream().filter(i -> i.getConfidence().ordinal() >= minimumConfidence.ordinal()).toList();
    }

    /**
     * Attempts to convert the input into a specified type with a minimum confidence level of LOW.
     *
     * @return An optional containing the converted input if successful, otherwise an empty optional.
     */
    default <T> Optional<T> into() {
        return into(Confidence.LOW);
    }

    /**
     * Attempts to convert the input into a specified type, given a minimum confidence level.
     *
     * @param minimumConfidence The minimum confidence level.
     * @return An optional containing the converted input if successful, otherwise an empty optional.
     */
    @SuppressWarnings("unchecked")
    default <T> Optional<T> into(Confidence minimumConfidence) {
        for (EdictValue<?> edictValue : getParsedValues(minimumConfidence)) {
            if (edictValue.getConfidence() == Confidence.HIGH) {
                try {
                    return Optional.of((T) edictValue.getValue());
                } catch (Throwable ignored) {

                }
            }
        }

        return Optional.empty();
    }

    /**
     * Attempts to convert the input into a specified type with a minimum confidence level of LOW.
     *
     * @return An optional containing the converted input if successful, otherwise an empty optional.
     */
    default <T> Optional<T> into(Class<T> type) {
        return into(type, Confidence.LOW);
    }

    /**
     * Attempts to convert the input into a specified type, given a minimum confidence level.
     *
     * @param minimumConfidence The minimum confidence level.
     * @return An optional containing the converted input if successful, otherwise an empty optional.
     */
    @SuppressWarnings("unchecked")
    default <T> Optional<T> into(Class<T> type, Confidence minimumConfidence) {
        for (EdictValue<?> edictValue : getParsedValues(minimumConfidence)) {
            if (edictValue.getConfidence() == Confidence.HIGH) {
                if (edictValue.getValue().getClass().equals(type)) {
                    try {
                        return Optional.of((T) edictValue.getValue());
                    } catch (Throwable ignored) {

                    }
                }
            }
        }

        return Optional.empty();
    }
}
