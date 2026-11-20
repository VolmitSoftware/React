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
import art.arcane.react.model.ReactEntity;
import art.arcane.react.platform.EntityEventHost;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;

import java.util.Arrays;
import java.util.List;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Entity Crowd Prevention tweak. Trims overcrowded livestock-style entity clusters before they create sustained tick pressure.")
public class TweakEntityCrowdPrevention extends ReactTweak {
  public static final String ID = "entity-crowd-prevention";
  /**
   * The maximum crowding factor an entity can have before it is purged
   */
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum entities allowed per cluster crowd in entity crowd prevention.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxEntitiesPerClusterCrowd = 10;
  /**
   * List of entity types to check for crowding
   */
  @art.arcane.react.util.project.config.ConfigDoc(value = "Entity types monitored for local crowding pressure and eligible for pruning.", impact = "Narrow this list to protect more mobs, or broaden it to enforce anti-crowding on additional entity types.")
  private List<String> mobsToPreventFromCrowding = Arrays.asList(
      "COW",
      "CHICKEN",
      "PIG",
      "SHEEP",
      "PIG"
  );
  /**
   * Prevents the entity from existing if its being suspended by the soulsand
   * bubble column
   */
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether entity crowd prevention applies prevent entity bubbling.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean preventEntityBubbling = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Protects player-named entities from entity crowd prevention.", impact = "Keep enabled to exclude entities with a custom name; disable it to make named entities eligible for removal.")
  private boolean protectNamedEntities = true;
  private transient Subscription ticks = Subscription.NONE;
  private transient volatile EntityHost entities;
  private transient volatile EntityEventHost events;

  public TweakEntityCrowdPrevention() {
    super(ID);
  }

  @Override
  public void onActivate() {
    EntityHost host = entities();
    ticks = host == null || mobsToPreventFromCrowding.isEmpty()
        ? Subscription.NONE
        : host.onTick(List.copyOf(mobsToPreventFromCrowding), this::onCrowdCheck);
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
    if (host == null || !removable(host, entity)) {
      return;
    }

    double crowdingFactor = ReactEntity.getCrowding(entity);
    if (crowdingFactor >= maxEntitiesPerClusterCrowd) {
      kill(host, entity);
    }
  }

  private boolean removable(EntityHost host, GameEntity entity) {
    return !host.protectsNamed(entity, protectNamedEntities) && !host.isProtected(entity, ReactOperation.PURGE);
  }

  private void kill(EntityHost host, GameEntity entity) {
    EntityEventHost checks = events();
    if (checks == null) {
      return;
    }
    int delay = (int) (20 * Math.random());
    Runnable removal = () -> {
      if (removable(host, entity)) {
        checks.removeLater(entity, 3);
      }
    };
    if (!J.runEntity(entity.handle(), removal, delay)) {
      removal.run();
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
