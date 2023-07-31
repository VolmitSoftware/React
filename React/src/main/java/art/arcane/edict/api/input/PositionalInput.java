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
 * The PositionalInput interface represents a specialized form of an EdictInput in the Edict command handling system
 * where the input is identified by its position in the command arguments.
 * <br><br>
 * The interface provides methods to retrieve the real position and the positional position of the input. The real
 * position is the actual location of the input in the command string, while the positional position is calculated by
 * only counting positional inputs, ignoring any mapped parameters.
 */
public interface PositionalInput {
    /**
     * The real position of this input mapped from the string
     *
     * @return the real position
     */
    int getRealPosition();

    /**
     * The position starting at 0, indexed by the POSITIONAL type only.
     * This means if there is a mapped parameter before this input it is ignored and wont increment.
     * This ensures that if there is 3 positional inputs there will only be 0 1 and 2 even if there are mapped params
     * in between the positional inputs.
     *
     * @return the positional position
     */
    int getPosition();
}
