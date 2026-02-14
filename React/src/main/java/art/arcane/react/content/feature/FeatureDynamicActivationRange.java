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
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class FeatureDynamicActivationRange extends ReactFeature implements Listener {
    public static final String ID = "dynamic-activation-range";
    private int tickIntervalMS = 1000;
    private int maxEntitiesSampledPerCycle = 240;
    private double minimumActivationRange = 18;
    private double maximumActivationRange = 64;
    private double currentActivationRange = maximumActivationRange;
    private double targetTickMS = 45;
    private double criticalTickMS = 70;
    private double minimumEntityAgeTicks = 100;
    private boolean ignoreTamedEntities = true;
    private boolean ignoreNamedEntities = true;

    public FeatureDynamicActivationRange() {
        super(ID);
    }

    @Override
    public void onActivate() {
        currentActivationRange = maximumActivationRange;
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
        double tickTime = React.sampler(SamplerTickTime.ID).sample();
        tuneRange(tickTime);
        J.s(this::applyActivationRange);
    }

    private void tuneRange(double tickTime) {
        if (tickTime > criticalTickMS) {
            currentActivationRange = Math.max(minimumActivationRange, currentActivationRange - 6);
            return;
        }

        if (tickTime > targetTickMS) {
            currentActivationRange = Math.max(minimumActivationRange, currentActivationRange - 2);
            return;
        }

        currentActivationRange = Math.min(maximumActivationRange, currentActivationRange + 1);
    }

    private void applyActivationRange() {
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

            int sample = Math.min(budget, entities.size());
            for (int i = 0; i < sample; i++) {
                Entity entity = entities.get(random.nextInt(entities.size()));
                budget--;
                manage(entity);
                if (budget <= 0) {
                    return;
                }
            }
        }
    }

    private void manage(Entity entity) {
        if (!canManage(entity)) {
            wake(entity);
            return;
        }

        if (React.hasNearbyPlayer(entity.getLocation(), currentActivationRange)) {
            wake(entity);
            return;
        }

        if (!ReactEntity.isPaused(entity)) {
            ReactEntity.setPaused(entity, true);
        }
    }

    private boolean canManage(Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity.isDead() || entity instanceof Player) {
            return false;
        }

        if (entity.getTicksLived() < minimumEntityAgeTicks) {
            return false;
        }

        if (ignoreNamedEntities && living.getCustomName() != null && !living.getCustomName().isBlank()) {
            return false;
        }

        return !(ignoreTamedEntities && living instanceof Tameable tameable && tameable.isTamed());
    }

    private void wake(Entity entity) {
        if (entity != null && !entity.isDead() && ReactEntity.isPaused(entity)) {
            ReactEntity.setPaused(entity, false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void on(EntityDamageEvent event) {
        wake(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void on(EntityTargetEvent event) {
        wake(event.getEntity());
        wake(event.getTarget());
    }
}
