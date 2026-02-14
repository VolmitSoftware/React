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
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class TweakSpawnerPlayerRadius extends ReactTweak implements Listener {
    public static final String ID = "spawner-player-radius";
    private double requiredPlayerDistance = 32;
    private boolean onlyMonsters = true;
    private boolean enforceSpawnerSpawns = true;
    private boolean enforceTrialSpawnerSpawns = true;

    public TweakSpawnerPlayerRadius() {
        super(ID);
    }

    @Override
    public void onActivate() {

    }

    @Override
    public void onDeactivate() {

    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void on(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        boolean spawnerReason = (reason == CreatureSpawnEvent.SpawnReason.SPAWNER && enforceSpawnerSpawns)
                || ("TRIAL_SPAWNER".equals(reason.name()) && enforceTrialSpawnerSpawns);

        if (!spawnerReason) {
            return;
        }

        if (onlyMonsters && !(event.getEntity() instanceof Monster)) {
            return;
        }

        if (!React.hasNearbyPlayer(event.getLocation(), requiredPlayerDistance)) {
            event.setCancelled(true);
        }
    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}
