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

package art.arcane.edict.api;

/**
 * An enumeration used to express the level of confidence in the interpretation of a parsed value.
 * Higher levels of confidence imply a greater certainty that the value accurately represents the
 * intended input, whereas lower levels suggest a higher degree of uncertainty and the likelihood
 * that the value was guessed based on the input.
 * <br><br>
 * The `Confidence` enum is typically used when multiple types can be inferred from a given input,
 * allowing higher confidence results to be favored over lower confidence ones.
 */
public enum Confidence {

    /**
     * This is the lowest level of confidence, indicating that the value cannot be accurately
     * inferred from the input.
     * <br><br>
     * For example, consider an integer parser and an input of "*". This cannot be converted to
     * an integer, hence an INVALID confidence is returned. In this case, a default value (like 0)
     * may be used instead.
     */
    INVALID,

    /**
     * This is a low level of confidence, indicating that the value could potentially be inferred from the input,
     * but it's uncertain.
     * <br><br>
     * For example, consider an enumeration of {APPLE, ORANGE, PINEAPPLE} and an input of "ange". It could
     * be inferred that the user intended to input "ORANGE", but this would be considered a LOW confidence result.
     * Another example would be an input of 'F' for an integer parser - it could potentially represent the number 16
     * in hexadecimal, but it's likely to be incorrect.
     */
    LOW,

    /**
     * This is the highest level of confidence, indicating that the parsed value is almost certainly an accurate
     * representation of the intended input.
     * <br><br>
     * HIGH confidence should only be used when calling `toString()` on the object would result in the original
     * input string or something very close to it (ignoring case).
     */
    HIGH,
}
