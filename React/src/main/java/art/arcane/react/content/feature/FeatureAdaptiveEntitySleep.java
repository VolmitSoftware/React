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

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class FeatureAdaptiveEntitySleep extends ReactFeature implements Listener {
    public static final String ID = "adaptive-entity-sleep";
    private int tickIntervalMS = 1000;
    private int maxEntitiesSampledPerCycle = 320;
    private int minimumEntityAgeTicks = 200;
    private double sleepBeyondNearestPlayer = 48;
    private boolean ignoreNamedEntities = true;
    private boolean ignoreTamedEntities = true;
    private boolean ignorePersistentEntities = true;
    private boolean ignoreVillagers = true;
    private boolean ignoreBosses = true;
    private boolean wakeOnDamage = true;
    private boolean wakeOnTarget = true;

    public FeatureAdaptiveEntitySleep() {
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
        J.s(this::applySleepScan);
    }

    private void applySleepScan() {
        int budget = Math.max(1, maxEntitiesSampledPerCycle);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (World world : Bukkit.getWorlds()) {
            if (budget <= 0) {
                return;
            }

            List<Entity> entities = world.getEntities();
            if (entities.isEmpty()) {
                continue;
            }

            int sampleCount = Math.min(entities.size(), budget);
            for (int i = 0; i < sampleCount; i++) {
                Entity entity = entities.get(random.nextInt(entities.size()));
                manageEntity(entity);
                budget--;

                if (budget <= 0) {
                    return;
                }
            }
        }
    }

    private void manageEntity(Entity entity) {
        if (!canManage(entity)) {
            wake(entity);
            return;
        }

        if (React.hasNearbyPlayer(entity.getLocation(), sleepBeyondNearestPlayer)) {
            wake(entity);
            return;
        }

        if (!ReactEntity.isPaused(entity)) {
            ReactEntity.setPaused(entity, true);
        }
    }

    private boolean canManage(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }

        if (entity instanceof Player || entity.isDead()) {
            return false;
        }

        if (entity.getTicksLived() < minimumEntityAgeTicks) {
            return false;
        }

        if (ignoreNamedEntities && living.getCustomName() != null && !living.getCustomName().isBlank()) {
            return false;
        }

        if (ignoreTamedEntities && living instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }

        if (ignoreVillagers && living instanceof Villager) {
            return false;
        }

        if (ignoreBosses && isBoss(living)) {
            return false;
        }

        return !ignorePersistentEntities || !living.isPersistent();
    }

    private boolean isBoss(LivingEntity entity) {
        return entity instanceof EnderDragon || entity instanceof Wither || entity instanceof Warden;
    }

    private void wake(Entity entity) {
        if (entity != null && !entity.isDead() && ReactEntity.isPaused(entity)) {
            ReactEntity.setPaused(entity, false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void on(EntityDamageEvent event) {
        if (wakeOnDamage) {
            wake(event.getEntity());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void on(EntityTargetEvent event) {
        if (!wakeOnTarget) {
            return;
        }

        wake(event.getEntity());
        wake(event.getTarget());
    }
}
