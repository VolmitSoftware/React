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

package art.arcane.react.content.tweak;

import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.platform.EntityEventHost;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;

import java.util.Arrays;
import java.util.List;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Entity Bubbler tweak. Prevents selected projectile or utility entities from being suspended indefinitely in bubble columns.")
public class TweakEntityBubbler extends ReactTweak {
  public static final String ID = "entity-bubbler";
  /**
   * List of entity types to check for crowding
   */
  @art.arcane.react.util.project.config.ConfigDoc(value = "Entity types that are removed when detected inside bubble-column or soul-sand bubble lift paths.", impact = "Add more types to aggressively clean bubble elevators, or remove types to allow those entities to travel in water columns.")
  private List<String> entitiedToPreventFromBeingBubbled = Arrays.asList(
      "ARROW",
      "ARMOR_STAND",
      "MINECART",
      "ENDER_PEARL",
      "SNOWBALL"
  );
  private transient Subscription ticks = Subscription.NONE;
  private transient volatile EntityHost entities;
  private transient volatile EntityEventHost events;

  public TweakEntityBubbler() {
    super(ID);
  }

  @Override
  public void onActivate() {
    EntityHost host = entities();
    ticks = host == null || entitiedToPreventFromBeingBubbled.isEmpty()
        ? Subscription.NONE
        : host.onTick(List.copyOf(entitiedToPreventFromBeingBubbled), this::onCrowdCheck);
  }

  @Override
  public void onDeactivate() {
    ticks.cancel();
    ticks = Subscription.NONE;
  }

  /**
   * Checks if the entity is crowded
   */
  public void onCrowdCheck(GameEntity entity) {
    EntityHost host = entities();
    EntityEventHost checks = events();
    if (host == null || checks == null || host.isProtected(entity, ReactOperation.DESPAWN)) {
      return;
    }

    if (checks.isBubbled(entity)) {
      kill(checks, entity);
    }
  }

  private void kill(EntityEventHost checks, GameEntity entity) {
    int delay = (int) (20 * Math.random());
    if (!J.runEntity(entity.handle(), () -> checks.removeLater(entity, 3), delay)) {
      checks.removeLater(entity, 3);
    }
  }

  private EntityHost entities() {
    EntityHost host = entities;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.entityHost().orElse(null);
      entities = host;
    }
    return host;
  }

  private EntityEventHost events() {
    EntityEventHost host = events;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.entityEventHost().orElse(null);
      events = host;
    }
    return host;
  }
}
