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

import art.arcane.edict.api.Confidence;
import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

/**
 * The IntegerBinaryParser is a parser that parses binary string representations into integers.
 * It implements the EdictParser interface and its generic type is Integer.
 *
 * @see EdictParser
 */
public class IntegerBinaryParser implements EdictParser<Integer> {
    @Override
    public EdictValue<Integer> parse(String s) {
        Confidence c = Confidence.LOW;
        if (s.toLowerCase().endsWith("i")) {
            s = s.substring(0, s.length() - 1);
        }

        if (s.toLowerCase().startsWith("0b")) {
            s = s.substring(2);
            c = Confidence.HIGH;
        }

        try {
            return of(Integer.parseInt(s, 2), c);
        } catch (NumberFormatException e) {
            return low(0);
        }
    }
}
