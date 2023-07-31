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
 * The StringParser class is an implementation of the EdictParser interface designed specifically to handle
 * the parsing of string representations into String objects.
 * <br><br>
 * It overrides the parse method from the EdictParser interface. As this class is designed to parse strings,
 * the parse method simply returns the input string within an EdictValue, with a high confidence level.
 * No parsing exceptions are expected in this case, as the input is already a string.
 *
 * @see EdictParser
 */
public class StringParser implements EdictParser<String> {
    @Override
    public EdictValue<String> parse(String s) {
        return high(s);
    }
}
