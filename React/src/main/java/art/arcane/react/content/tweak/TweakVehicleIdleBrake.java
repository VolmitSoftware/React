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
import art.arcane.react.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class TweakVehicleIdleBrake extends ReactTweak {
    public static final String ID = "vehicle-idle-brake";
    private int tickIntervalMS = 1000;
    private int maxVehiclesSampledPerWorld = 180;
    private double minVelocitySquared = 0.0004;
    private double maxDistanceWithoutPlayer = 48;
    private boolean onlyEmptyVehicles = true;
    private boolean brakeMinecarts = true;
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
