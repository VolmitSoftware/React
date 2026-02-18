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

package art.arcane.react.content.directorcommand;

import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.scheduling.J;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

@Director(
    name = "debug",
    origin = DirectorOrigin.BOTH,
    description = "This is the Debugging command for Various things."
)
public class CommandDebug implements DirectorExecutor {
  @Director(
      name = "entity-data",
      aliases = {"ed"},
      description = "Show Entity Data for the entity looked at",
      origin = DirectorOrigin.PLAYER,
      sync = true
  )
  public void entityData() {
    Vector look = player().getLocation().getDirection().multiply(1);
    Location buf = player().getLocation().clone().add(look);
    ray:
    for (int i = 0; i < 16; i++) {
      buf.add(look);
      for (Entity j : buf.getWorld().getNearbyEntities(buf, 2, 2, 2)) {
        if (j.equals(player())) {
          continue;
        }
        j.setGlowing(true);
        J.runEntity(j, () -> j.setGlowing(false), 1);
        player().sendMessage("Priority: " + Form.f((int) ReactEntity.getPriority(j)));
        player().sendMessage("Crowding: " + Form.f((int) ReactEntity.getCrowding(j)));
        player().sendMessage("Player N: " + Form.f(ReactEntity.getNearestPlayer(j), 1));
        player().sendMessage("Updated : " + Form.duration(ReactEntity.getStaleness(j), 0) + " ago");
        break ray;
      }
    }
  }
}
