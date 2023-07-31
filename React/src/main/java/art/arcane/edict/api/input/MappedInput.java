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

/**
 * The MappedInput interface represents a specialized form of an EdictInput in the Edict command handling system
 * where the input is mapped to a key. This mapping can be useful in situations where inputs are identified
 * or categorized by certain keys.
 * <br><br>
 * The interface provides a method to retrieve the key to which the input is mapped.
 */
public interface MappedInput {
    /**
     * The key this input is mapped to
     *
     * @return the key
     */
    String getKey();
}
