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
 * The IntegerParser class is an implementation of the EdictParser interface, specifically
 * designed to parse strings into Integer objects.
 * <br><br>
 * The class overrides the parse method of the EdictParser interface. It first checks if the
 * string ends with the character 'i' (in a case-insensitive manner), and if it does, it removes this
 * character. Then it attempts to parse the remaining string into an integer.
 * <br><br>
 * If the parsing is successful, it returns an EdictValue containing the parsed integer and high confidence.
 * In case a NumberFormatException is thrown during the parsing process, it returns an EdictValue with
 * a default value of 0 and low confidence, indicating a less certain or incorrect parsing.
 *
 * @see EdictParser
 */
public class IntegerParser implements EdictParser<Integer> {
    @Override
    public EdictValue<Integer> parse(String s) {
        try {
            if (s.toLowerCase().endsWith("i")) {
                s = s.substring(0, s.length() - 1);
            }

            return high(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return low(0);
        }
    }
}
