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
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

@Director(
    name = "debug",
    origin = DirectorOrigin.BOTH,
    description = "React debugging commands",
    descriptionKey = "command.description.debug"
)
public class CommandDebug implements DirectorExecutor {
  @Director(
      name = "entity-data",
      aliases = {"ed"},
      description = "Show data for the entity being looked at",
      descriptionKey = "command.description.debug.entity",
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
        if (j instanceof LivingEntity living) {
          J.runEntity(living, () -> living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20, 0, false, false)));
        }
        ReactLanguage.send(player(), CommandMessages.DEBUG_PRIORITY, MessageArgument.untrusted("value", Form.f((int) ReactEntity.getPriority(j))));
        ReactLanguage.send(player(), CommandMessages.DEBUG_CROWDING, MessageArgument.untrusted("value", Form.f((int) ReactEntity.getCrowding(j))));
        ReactLanguage.send(player(), CommandMessages.DEBUG_NEAREST_PLAYER, MessageArgument.untrusted("value", Form.f(ReactEntity.getNearestPlayer(j), 1)));
        ReactLanguage.send(player(), CommandMessages.DEBUG_UPDATED, MessageArgument.untrusted("duration", Form.duration(ReactEntity.getStaleness(j), 0)));
        break ray;
      }
    }
  }
}
