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

package art.arcane.edict.content.context;

import art.arcane.edict.api.context.EdictContext;
import art.arcane.edict.api.context.EdictContextResolver;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * The WorldContext class implements the EdictContextResolver interface for the type {@link World}.
 * It provides functionality to resolve a {@link World} object from the current {@link EdictContext}.
 * <br><br>
 * This resolution is useful in scenarios where a command executor needs access to the World associated
 * with the player issuing the command, as it provides an easy way to retrieve this World in the context
 * of an Edict command execution.
 */
public class WorldContext implements EdictContextResolver<World> {

    /**
     * Provides a convenient way to retrieve the World associated with the current EdictContext.
     * <br><br>
     * This static method returns an Optional containing the resolved World if the CommandSender within
     * the current EdictContext is a Player, or an empty Optional otherwise.
     *
     * @return an Optional containing the resolved World if the CommandSender within the current EdictContext is a Player, or an empty Optional otherwise
     */
    public static Optional<World> get() {
        return EdictContext.get().resolve(WorldContext.class);
    }

    /**
     * Resolves a World object from the provided EdictContext.
     * <br><br>
     * If the CommandSender within the context is a Player, the method returns an Optional containing the
     * World where the Player is currently located. If the CommandSender is not a Player, the method returns
     * an empty Optional.
     *
     * @param context the EdictContext from which to resolve the World
     * @return an Optional containing the resolved World if the CommandSender is a Player, or an empty Optional otherwise
     */
    @Override
    public Optional<World> resolve(EdictContext context) {
        return context.getSender() instanceof Player p ? Optional.of(p.getWorld()) : Optional.empty();
    }
}
