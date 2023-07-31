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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Represents a parsed value with an associated confidence level within the Edict command handling system.
 * <br><br>
 * The value is the parsed result produced by an {@link EdictParser}, and confidence represents how certain the parser
 * is that the value was parsed correctly or is appropriate in the context it was used.
 * <br><br>
 * This class is a fundamental part of the Edict command handling system and is used throughout for carrying parsed values
 * along with the confidence of their validity.
 *
 * @param <T> The type of the parsed value.
 */
@Data
@AllArgsConstructor
@Builder
public class EdictValue<T> {
    /**
     * The parsed value of type T.
     * This is the output produced by an EdictParser after parsing an input string.
     */
    private final T value;

    /**
     * The confidence associated with the parsed value.
     * It indicates how certain the parser is that the value was parsed correctly or is appropriate in the context it was used.
     * The confidence is of type Confidence, which is an enum with levels INVALID, LOW, and HIGH.
     */
    private final Confidence confidence;
}
