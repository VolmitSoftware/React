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

package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

/**
 * The DoubleParser class is an implementation of the EdictParser interface for parsing Double values from a String.
 * It tries to parse the provided string as a double, returning a high-confidence Double value if successful.
 * If the string ends with "d", this character is ignored during parsing.
 * In case of a parsing failure (if the string cannot be parsed into a Double), the parser returns a low-confidence default Double value of 0.0.
 */
public class DoubleParser implements EdictParser<Double> {
    @Override
    public EdictValue<Double> parse(String s) {
        try {
            if (s.toLowerCase().endsWith("d")) {
                s = s.substring(0, s.length() - 1);
            }

            return high(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return low(0D);
        }
    }
}
