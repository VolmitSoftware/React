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
 * The FloatParser class is an implementation of the EdictParser interface for parsing Float values from a String.
 * It attempts to parse the provided string as a float, returning a high-confidence Float value if successful.
 * If the string ends with "d", this character is ignored during parsing.
 * In case of a parsing failure (if the string cannot be parsed into a Float), the parser returns a low-confidence default Float value of 0.0F.
 */
public class FloatParser implements EdictParser<Float> {
    @Override
    public EdictValue<Float> parse(String s) {
        try {
            if (s.toLowerCase().endsWith("d")) {
                s = s.substring(0, s.length() - 1);
            }

            return high(Float.parseFloat(s));
        } catch (NumberFormatException e) {
            return low(0F);
        }
    }
}
