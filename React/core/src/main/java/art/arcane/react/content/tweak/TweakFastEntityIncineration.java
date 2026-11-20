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

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Fast Entity Incineration tweak. Removes burning monsters under configured proximity rules to cut lingering fire-tick cost.")
public class TweakFastEntityIncineration extends ReactTweak {
  public static final String ID = "fast-entity-incineration";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Distance from players before fast entity incineration applies incineration behavior (blocks).", impact = "Higher values allow cleanup farther from players; lower values keep cleanup closer to active players.")
  private double incinerationBeyondNearestPlayer = 32;
  private transient Subscription damage = Subscription.NONE;
  private transient volatile EntityHost entities;
  private transient volatile EntityEventHost events;

  public TweakFastEntityIncineration() {
    super(ID);
  }

  @Override
  public void onActivate() {
    EntityEventHost host = events();
    damage = host == null ? Subscription.NONE : host.onDamage(this::on);
  }

  @Override
  public void onDeactivate() {
    damage.cancel();
    damage = Subscription.NONE;
  }

  void on(EntityEventHost.Damage event) {
    EntityEventHost checks = events();
    EntityHost host = entities();
    GameEntity entity = event.entity();
    if (checks != null && host != null && event.isFireTick() && checks.isMonster(entity)
        && !host.isProtected(entity, ReactOperation.DESPAWN)
        && !checks.nearPlayer(entity, incinerationBeyondNearestPlayer)) {
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
