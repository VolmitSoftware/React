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

package art.arcane.react.util.decree;


import art.arcane.react.React;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.decree.DecreeSystemSupport;

public final class DecreeSystem {
    public static final KList<DecreeParameterHandler<?>> handlers = React.initialize("art.arcane.react.util.decree.handlers", null).convert((i) -> (DecreeParameterHandler<?>) i);

    private DecreeSystem() {
    }

    /**
     * Get the handler for the specified type
     *
     * @param type The type to handle
     * @return The corresponding {@link DecreeParameterHandler}, or null
     */
    public static DecreeParameterHandler<?> getHandler(Class<?> type) {
        DecreeParameterHandler<?> handler = DecreeSystemSupport.getHandler(handlers, type, (h, t) -> h.supports(t));
        if (handler != null) {
            return handler;
        }

        React.error("Unhandled type in Decree Parameter: " + type.getName() + ". This is bad!");
        return null;
    }
}
