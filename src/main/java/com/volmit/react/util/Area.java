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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;


/**
 * Used to Create an instance of a spherical area based on a central location
 * Great for efficiently checking if an entity is within a spherical area.
 *
 * @author cyberpwn
 */
public class Area {
    private Location location;
    private Double radius;

    /**
     * Used to instantiate a new "area" in which you can check if entities are
     * within this area.
     *
     * @param location The center location of the area
     * @param radius   The radius used as a double.
     */
    public Area(Location location, Double radius) {
        this.location = location;
        this.radius = radius;
    }

    /**
     * Used to instantiate a new "area" in which you can check if entities are
     * within this area.
     *
     * @param location The center location of the area
     * @param radius   The radius used as an int.
     */
    public Area(Location location, Integer radius) {
        this.location = location;
        this.radius = (double) radius;
    }

    public static boolean within(Location center, Location target, double rad) {
        return new Area(center, rad).isWithin(target);
    }

    public Cuboid toCuboid() {
        return new Cuboid(location.clone().add(radius, radius, radius), location.clone().subtract(radius, radius, radius));
    }

    /**
     * Calculate the <STRONG>ESTIMATED distance</STRONG> from the center of this
     * area, to the given location <STRONG>WARNING: This uses newton's method,
     * be careful on how accurate you need this. As it is meant for FAST
     * calculations with minimal load.</STRONG>
     *
     * @param location The given location to calculate a distance from the center.
     * @return Returns the distance of location from the center.
     */
    public Double distance(Location location) {
        double c = this.location.distanceSquared(location);
        double t = c;

        for (int i = 0; i < 3; i++) {
            t = (c / t + t) / 2.0;
        }

        return t;
    }

    /**
     * Calculate the <STRONG>EXACT distance</STRONG> from the center of this
     * area, to the given location <STRONG>WARNING: This uses the sqrt function,
     * be careful on how heavy you call this.</STRONG>
     *
     * @param location The given location to calculate a distance from the center.
     * @return Returns the distance of location from the center.
     */
    public Double slowDistance(Location location) {
        return this.location.distance(location);
    }

    /**
     * Check to see weather a location is within the area
     *
     * @param location The location to measure from the center.
     * @return Returns True if within; False if not.
     */
    public boolean isWithin(Location location) {
        return this.location.distance(location) <= (radius * radius);
    }

    /**
     * But does it have any entities?
     */
    public boolean hasEntities() {
        return getNearbyEntities().length > 0;
    }

    /**
     * Get all nearby entities matching the given entity type
     *
     * @param type the entity type
     * @return the nearby entities matching the given type
     */
    public Entity[] getNearbyEntities(EntityType type) {
        List<Entity> e = new ArrayList<>(List.of(getNearbyEntities()));
        e.removeIf(i -> !i.getType().equals(type));
        return e.toArray(new Entity[0]);
    }

    /**
     * Get nearby entities which match the following class
     *
     * @param entityClass the entity class
     * @return the nearby entities assignable from the given class
     */
    public Entity[] getNearbyEntities(Class<? extends Entity> entityClass) {
        List<Entity> e = new ArrayList<>(List.of(getNearbyEntities()));

        e.removeIf(i -> !i.getClass().isAssignableFrom(entityClass));

        return e.toArray(new Entity[0]);
    }

    /**
     * Get ALL entities within the area. <STRONG>NOTE: This is EVERY entity, not
     * just LivingEntities. Drops, Particles, Mobs, Players, Everything</STRONG>
     *
     * @return Returns an Entity[] array of all entities within the given area.
     */
    public Entity[] getNearbyEntities() {
        try {
            int chunkRadius = (int) (radius < 16 ? 1 : (radius - (radius % 16)) / 16);
            HashSet<Entity> radiusEntities = new HashSet<Entity>();

            for (int chX = 0 - chunkRadius; chX <= chunkRadius; chX++) {
                for (int chZ = 0 - chunkRadius; chZ <= chunkRadius; chZ++) {
                    int x = (int) location.getX(), y = (int) location.getY(), z = (int) location.getZ();

                    for (Entity e : new Location(location.getWorld(), x + (chX * 16), y, z + (chZ * 16)).getChunk().getEntities()) {
                        if (e.getLocation().distanceSquared(location) <= radius * radius && e.getLocation().getBlock() != location.getBlock()) {
                            radiusEntities.add(e);
                        }
                    }
                }
            }

            return radiusEntities.toArray(new Entity[radiusEntities.size()]);
        } catch (Exception e) {
            return new ArrayList<Entity>().toArray(new Entity[0]);
        }
    }

    /**
     * Get all players within the area.
     *
     * @return Returns an Player[] array of all players within the given area.
     */
    public Player[] getNearbyPlayers() {
        List<Player> px = new ArrayList<>();

        for (Entity i : getNearbyEntities()) {
            if (i.getType().equals(EntityType.PLAYER)) {
                px.add((Player) i);
            }
        }

        return px.toArray(new Player[0]);
    }

    /**
     * Get the defined center location
     *
     * @return Returns the center location of the area
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Set the defined center location
     *
     * @param location The new location to be set
     */
    public void setLocation(Location location) {
        this.location = location;
    }

    /**
     * Gets the area's radius
     *
     * @return Returns the area's radius
     */
    public Double getRadius() {
        return radius;
    }

    /**
     * Set the area's radius
     *
     * @param radius The new radius to be set
     */
    public void setRadius(Double radius) {
        this.radius = radius;
    }

    /**
     * Pick a random location in this radius
     */
    public Location random() {
        Random r = new Random();
        double x = radius * ((r.nextDouble() - 0.5) * 2);
        double y = radius * ((r.nextDouble() - 0.5) * 2);
        double z = radius * ((r.nextDouble() - 0.5) * 2);

        return location.clone().add(x, y, z);
    }
}
