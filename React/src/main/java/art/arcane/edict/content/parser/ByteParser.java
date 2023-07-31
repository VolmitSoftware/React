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
 * The ByteParser class is an implementation of the EdictParser interface for parsing Byte values from a String.
 * It attempts to parse the provided string as a byte, returning a high-confidence Byte value if successful.
 * If the string ends with "b", this character is ignored during parsing.
 * If parsing fails, the parser returns a low-confidence default Byte value of 0.
 */
public class ByteParser implements EdictParser<Byte> {
    @Override
    public EdictValue<Byte> parse(String s) {
        try {
            if (s.toLowerCase().endsWith("b")) {
                s = s.substring(0, s.length() - 1);
            }

            return high(Byte.parseByte(s));
        } catch (NumberFormatException e) {
            return low((byte) 0);
        }
    }
}
