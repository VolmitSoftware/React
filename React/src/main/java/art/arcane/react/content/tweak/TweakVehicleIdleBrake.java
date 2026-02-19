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

import art.arcane.react.React;
import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Vehicle Idle Brake tweak. Applies braking to distant idle vehicles to reduce runaway movement and physics overhead.")
public class TweakVehicleIdleBrake extends ReactTweak {
  public static final String ID = "vehicle-idle-brake";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for vehicle idle brake in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 1000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum vehicles sampled allowed per world in vehicle idle brake.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxVehiclesSampledPerWorld = 180;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum velocity squared required by vehicle idle brake.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
  private double minVelocitySquared = 0.0004;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum distance without player allowed by vehicle idle brake.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private double maxDistanceWithoutPlayer = 48;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether vehicle idle brake applies only empty vehicles.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean onlyEmptyVehicles = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether vehicle idle brake applies brake minecarts.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean brakeMinecarts = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether vehicle idle brake applies brake boats.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean brakeBoats = true;

  public TweakVehicleIdleBrake() {
    super(ID);
  }

  @Override
  public void onActivate() {

  }

  @Override
  public void onDeactivate() {

  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    if (J.isFoliaThreading()) {
      scanPlayersFolia();
      return;
    }

    J.s(this::scanWorlds);
  }

  private void scanWorlds() {
    for (World world : Bukkit.getWorlds()) {
      if (brakeMinecarts) {
        sampleAndBrake(world.getEntitiesByClass(Minecart.class));
      }

      if (brakeBoats) {
        sampleAndBrake(world.getEntitiesByClass(Boat.class));
      }
    }
  }

  private void scanPlayersFolia() {
    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
    if (players.isEmpty()) {
      return;
    }

    ThreadLocalRandom random = ThreadLocalRandom.current();
    int samples = Math.min(players.size(), Math.max(1, maxVehiclesSampledPerWorld / 12));
    for (int i = 0; i < samples; i++) {
      Player player = players.get(random.nextInt(players.size()));
      J.runEntity(player, () -> sampleAndBrakeAroundPlayer(player));
    }
  }

  private void sampleAndBrakeAroundPlayer(Player player) {
    if (player == null || !player.isOnline() || !J.isOwnedByCurrentRegion(player)) {
      return;
    }

    List<Entity> nearby = player.getNearbyEntities(
        maxDistanceWithoutPlayer + 24,
        Math.max(32, maxDistanceWithoutPlayer),
        maxDistanceWithoutPlayer + 24
    );
    if (nearby.isEmpty()) {
      return;
    }

    if (brakeMinecarts) {
      sampleAndBrake(filterVehicles(nearby, Minecart.class));
    }
    if (brakeBoats) {
      sampleAndBrake(filterVehicles(nearby, Boat.class));
    }
  }

  private <T extends Entity> List<T> filterVehicles(List<Entity> nearby, Class<T> type) {
    List<T> filtered = new ArrayList<>();
    for (Entity entity : nearby) {
      if (type.isInstance(entity)) {
        filtered.add(type.cast(entity));
      }
    }
    return filtered;
  }

  private <T extends Entity> void sampleAndBrake(Collection<T> vehicleCollection) {
    List<T> vehicles = vehicleCollection instanceof List<T> list ? list : new ArrayList<>(vehicleCollection);
    if (vehicles.isEmpty()) {
      return;
    }

    int sample = Math.min(maxVehiclesSampledPerWorld, vehicles.size());
    ThreadLocalRandom random = ThreadLocalRandom.current();

    for (int i = 0; i < sample; i++) {
      T vehicle = vehicles.get(random.nextInt(vehicles.size()));
      if (!shouldBrake(vehicle)) {
        continue;
      }

      vehicle.setVelocity(new Vector(0, 0, 0));
      if (vehicle instanceof Minecart minecart) {
        minecart.setDerailedVelocityMod(new Vector(0, 0, 0));
        minecart.setFlyingVelocityMod(new Vector(0, 0, 0));
      }
    }
  }

  private boolean shouldBrake(Entity vehicle) {
    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(vehicle)) {
      return false;
    }

    if (vehicle.isDead()) {
      return false;
    }

    if (onlyEmptyVehicles && !vehicle.getPassengers().isEmpty()) {
      return false;
    }

    if (vehicle.getVelocity().lengthSquared() < minVelocitySquared) {
      return false;
    }

    return !React.hasNearbyPlayer(vehicle.getLocation(), maxDistanceWithoutPlayer);
  }
}
