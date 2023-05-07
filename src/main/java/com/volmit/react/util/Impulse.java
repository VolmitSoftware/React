/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.react.util;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class Impulse {
    private final List<Entity> ignore;
    private double radius;
    private double forceMax;
    private double forceMin;
    private double damageMin;
    private double damageMax;

    public Impulse(double radius) {
        ignore = new ArrayList<>();
        this.radius = radius;
        this.forceMax = 1;
        this.forceMin = 0;
        this.damageMax = 1;
        this.damageMin = 0;
    }

    public Impulse radius(double radius) {
        this.radius = radius;
        return this;
    }

    public Impulse force(double force) {
        this.forceMax = force;
        return this;
    }

    public Impulse force(double forceMax, double forceMin) {
        this.forceMax = forceMax;
        this.forceMin = forceMin;
        return this;
    }

    public Impulse damage(double damage) {
        this.damageMax = damage;
        return this;
    }

    public Impulse damage(double damageMax, double damageMin) {
        this.damageMax = damageMax;
        this.damageMin = damageMin;
        return this;
    }

    public void punch(Location at) {
        Area a = new Area(at, radius);

        for (Entity i : a.getNearbyEntities()) {
            if (ignore.contains(i)) {
                continue;
            }

            Vector force = VectorMath.direction(at, i.getLocation());
            double damage = 0;
            double distance = i.getLocation().distance(at);

            if (forceMin < forceMax) {
                force.clone().multiply(((1D - (distance / radius)) * (forceMax - forceMin)) + forceMin);
            }

            if (damageMin < damageMax) {
                damage = ((1D - (distance / radius)) * (damageMax - damageMin)) + damageMin;
            }

            try {
                if (i instanceof LivingEntity && damage > 0) {
                    ((LivingEntity) i).damage(damage);
                }

                i.setVelocity(i.getVelocity().add(force));
            } catch (Exception e) {

            }
        }
    }

    public Impulse ignore(Entity player) {
        ignore.add(player);
        return this;
    }
}