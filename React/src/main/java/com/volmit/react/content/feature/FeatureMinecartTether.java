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

package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.core.controller.EntityController;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

public class FeatureMinecartTether extends ReactFeature implements Listener {
    public static final String ID = "minecart-tether";
    private double maxBlockDistance = 32;

    public FeatureMinecartTether() {
        super(ID);
    }

    @Override
    public void onActivate() {
        React.controller(EntityController.class).registerEntityTickListener(EntityType.MINECART, (e) -> onMinecart((Minecart) e));
        React.controller(EntityController.class).registerEntityTickListener(EntityType.MINECART_CHEST, (e) -> onMinecart((Minecart) e));
        React.controller(EntityController.class).registerEntityTickListener(EntityType.MINECART_FURNACE, (e) -> onMinecart((Minecart) e));
        React.controller(EntityController.class).registerEntityTickListener(EntityType.MINECART_COMMAND, (e) -> onMinecart((Minecart) e));
        React.controller(EntityController.class).registerEntityTickListener(EntityType.MINECART_HOPPER, (e) -> onMinecart((Minecart) e));
        React.controller(EntityController.class).registerEntityTickListener(EntityType.MINECART_TNT, (e) -> onMinecart((Minecart) e));
        React.controller(EntityController.class).registerEntityTickListener(EntityType.MINECART_MOB_SPAWNER, (e) -> onMinecart((Minecart) e));
    }

    public void onMinecart(Minecart entity) {
        if (entity.getVelocity().getX() != 0 || entity.getVelocity().getY() != 0 || entity.getVelocity().getZ() != 0
                || entity.getFlyingVelocityMod().getX() != 0 || entity.getFlyingVelocityMod().getY() != 0 || entity.getFlyingVelocityMod().getZ() != 0
                || entity.getDerailedVelocityMod().getX() != 0 || entity.getDerailedVelocityMod().getY() != 0 || entity.getDerailedVelocityMod().getZ() != 0
        ) {
            if (!React.hasNearbyPlayer(entity.getLocation(), maxBlockDistance)) {
                entity.setVelocity(new Vector(0, 0, 0));
                entity.setDerailedVelocityMod(new Vector(0, 0, 0));
                entity.setFlyingVelocityMod(new Vector(0, 0, 0));
            }
        }
    }

    @Override
    public void onDeactivate() {

    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}
