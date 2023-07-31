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

package art.arcane.edict.api.context;

import java.util.Optional;

/**
 * The `EdictContextResolver` is a functional interface used for resolving objects of a certain type
 * within a given `EdictContext`. An implementation of this interface should provide logic to extract or
 * create the desired object from the provided context.
 * <br><br>
 * This interface can be used to provide a flexible mechanism for retrieving or creating contextual objects
 * in an `Edict` operation.
 *
 * @param <T> the type of object to resolve from the context
 * @see EdictContext
 */
@FunctionalInterface
public interface EdictContextResolver<T> {
    /**
     * This method is intended to be implemented to resolve an object of type T from a given `EdictContext`.
     * The method should return an Optional containing the resolved object if it could be successfully resolved,
     * or an empty Optional if it could not.
     *
     * @param context the `EdictContext` from which to resolve the object
     * @return an Optional containing the resolved object if it could be successfully resolved, or an empty Optional if not
     */
    Optional<T> resolve(EdictContext context);
}
