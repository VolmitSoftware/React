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
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Optional;

public class TargetBlockContext implements EdictContextResolver<Block> {
    public static Optional<Block> get() {
        return EdictContext.get().resolve(TargetBlockContext.class);
    }

    @Override
    public Optional<Block> resolve(EdictContext context) {
        return context.getSender() instanceof Player p ? Optional.of(p.getTargetBlockExact(5)) : Optional.empty();
    }
}
