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
 * The BooleanParser class is an implementation of the EdictParser interface for parsing Boolean values from a String.
 * It checks whether the provided string is "true" or "false" (ignoring case) and returns a corresponding Boolean value.
 * If the provided string is not "true" or "false", the parser returns a low-confidence Boolean value.
 */
public class BooleanParser implements EdictParser<Boolean> {
    @Override
    public EdictValue<Boolean> parse(String s) {
        boolean v = s.equalsIgnoreCase("true");

        if (v || s.equalsIgnoreCase("false")) {
            return high(v);
        }

        return low(v);
    }
}
