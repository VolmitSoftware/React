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

package art.arcane.edict.api.input;

import art.arcane.edict.api.parser.EdictValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

/**
 * The EdictInputHolder class is an implementation of the EdictInput interface. It provides a concrete representation
 * for an input to an Edict command in the Edict command handling system.
 * <br><br>
 * This class holds a list of parsed values which represent the interpreted values of the input. These parsed values
 * each have an associated level of confidence about their validity.
 */
@Builder
@Data
@AllArgsConstructor
public class EdictInputHolder implements EdictInput {
    /**
     * The list of parsed values that this input holds.
     */
    @Singular
    private final List<EdictValue<?>> parsedValues;
}
