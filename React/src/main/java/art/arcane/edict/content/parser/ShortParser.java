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
 * The ShortParser class is an implementation of the EdictParser interface designed to handle the parsing of
 * string representations into Short objects.
 * <br><br>
 * It overrides the parse method from the EdictParser interface. If the string to be parsed ends with 's'
 * (in a case-insensitive manner), this character is removed before the parsing attempt.
 * <br><br>
 * If the parsing is successful, it returns an EdictValue containing the parsed short and high confidence.
 * In the event of a NumberFormatException, it returns an EdictValue with a default value of 0 (cast to short)
 * and low confidence, indicating a less certain or incorrect parsing.
 *
 * @see EdictParser
 */
public class ShortParser implements EdictParser<Short> {
    @Override
    public EdictValue<Short> parse(String s) {
        try {
            if (s.toLowerCase().endsWith("s")) {
                s = s.substring(0, s.length() - 1);
            }

            return high(Short.parseShort(s));
        } catch (NumberFormatException e) {
            return low((short) 0);
        }
    }
}
