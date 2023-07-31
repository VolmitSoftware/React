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

import art.arcane.edict.api.Confidence;

/**
 * Functional interface that defines the contract for a parser in the Edict command handling system.
 * <br><br>
 * An implementation of this interface is responsible for parsing a String into an object of type T, and
 * producing an {@link EdictValue} that encapsulates both the parsed object and an associated confidence level.
 * The interface provides several default methods to conveniently produce EdictValue instances with
 * specific confidence levels.
 *
 * @param <T> The type of the object that the parser is expected to parse into.
 */
@FunctionalInterface
public interface EdictParser<T> {
    /**
     * Parses a string into an object of type T, wrapped in an EdictValue.
     * The implementation is expected to derive the confidence level as part of the parsing process.
     *
     * @param s the string to parse
     * @return an EdictValue encapsulating the parsed object and an associated confidence level
     */
    EdictValue<T> parse(String s);

    /**
     * Creates an EdictValue for the given object with a confidence level of LOW.
     *
     * @param value the object to be encapsulated in an EdictValue
     * @return an EdictValue encapsulating the object with a LOW confidence level
     */
    default EdictValue<T> of(T value) {
        return of(value, Confidence.LOW);
    }

    /**
     * Creates an EdictValue for the given object with a specified confidence level.
     *
     * @param value the object to be encapsulated in an EdictValue
     * @param c     the confidence level associated with the value
     * @return an EdictValue encapsulating the object with a specified confidence level
     */
    default EdictValue<T> of(T value, Confidence c) {
        return EdictValue.<T>builder()
                .value(value)
                .confidence(c)
                .build();
    }

    /**
     * Creates an EdictValue for the given object with a confidence level of HIGH.
     *
     * @param value the object to be encapsulated in an EdictValue
     * @return an EdictValue encapsulating the object with a HIGH confidence level
     */
    default EdictValue<T> high(T value) {
        return of(value, Confidence.HIGH);
    }

    /**
     * Creates an EdictValue for the given object with a confidence level of LOW.
     *
     * @param value the object to be encapsulated in an EdictValue
     * @return an EdictValue encapsulating the object with a LOW confidence level
     */
    default EdictValue<T> low(T value) {
        return of(value, Confidence.LOW);
    }

    /**
     * Creates an EdictValue for the given object with a confidence level of INVALID.
     *
     * @param value the object to be encapsulated in an EdictValue
     * @return an EdictValue encapsulating the object with an INVALID confidence level
     */
    default EdictValue<T> invalid(T value) {
        return of(value, Confidence.INVALID);
    }
}

