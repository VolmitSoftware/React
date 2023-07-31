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

package com.volmit.react.content.command;

import art.arcane.edict.Edict;
import art.arcane.edict.api.context.EdictContext;
import com.volmit.react.model.ReactEntity;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

public class CommandUtil {
    @Edict.PlayerOnly
    @Edict.Command("/react util entity-data")
    @Edict.Aliases({"/react u ed", "/react u entity-data", "/react u edata", "/react util edata"})
    public static void ed() {
        Vector look = EdictContext.player().getLocation().getDirection().multiply(1);
        Location buf = EdictContext.player().getLocation().clone().add(look);

        ray:
        for (int i = 0; i < 16; i++) {
            buf.add(look);

            for (Entity j : buf.getWorld().getNearbyEntities(buf, 2, 2, 2)) {
                if (j.equals(EdictContext.player())) {
                    continue;
                }

                j.setGlowing(true);

                J.s(() -> j.setGlowing(false), 1);

                EdictContext.sender().sendMessage("Priority: " + Form.f((int) ReactEntity.getPriority(j)));
                EdictContext.sender().sendMessage("Crowding: " + Form.f((int) ReactEntity.getCrowding(j)));
                EdictContext.sender().sendMessage("Player N: " + Form.f(ReactEntity.getNearestPlayer(j), 1));
                EdictContext.sender().sendMessage("Updated : " + Form.duration(ReactEntity.getStaleness(j), 0) + " ago");
                break ray;
            }
        }
    }
}
