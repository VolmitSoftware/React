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

package art.arcane.react.content.feature;

import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.platform.EntityEventHost;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.VehicleHost;

import java.util.List;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Minecart Tether feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureMinecartTether extends ReactFeature {
  public static final String ID = "minecart-tether";
  private static final List<String> MINECART_TYPES = List.of(
      "MINECART",
      "CHEST_MINECART",
      "FURNACE_MINECART",
      "COMMAND_BLOCK_MINECART",
      "HOPPER_MINECART",
      "TNT_MINECART",
      "SPAWNER_MINECART"
  );
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum block distance allowed by minecart tether.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private double maxBlockDistance = 32;
  private transient Subscription ticks = Subscription.NONE;
  private transient volatile VehicleHost vehicles;
  private transient volatile EntityEventHost events;

  public FeatureMinecartTether() {
    super(ID);
  }

  @Override
  public void onActivate() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    EntityHost host = platform == null ? null : platform.entityHost().orElse(null);
    ticks = host == null ? Subscription.NONE : host.onTick(MINECART_TYPES, this::onMinecart);
  }

  @Override
  public void onDeactivate() {
    ticks.cancel();
    ticks = Subscription.NONE;
  }

  public void onMinecart(GameEntity entity) {
    VehicleHost host = vehicles();
    EntityEventHost checks = events();
    if (host == null || checks == null) {
      return;
    }
    if (host.isMoving(entity) && !checks.nearPlayer(entity, maxBlockDistance)) {
      host.stop(entity);
    }
  }

  private VehicleHost vehicles() {
    VehicleHost host = vehicles;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.vehicleHost().orElse(null);
      vehicles = host;
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
