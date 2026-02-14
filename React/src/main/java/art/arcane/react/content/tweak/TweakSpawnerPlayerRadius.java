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

@art.arcane.react.util.config.ConfigDescription("Configuration for Spawner Player Radius tweak. Blocks spawner and trial-spawner spawns when players are outside the required activation distance.")
public class TweakSpawnerPlayerRadius extends ReactTweak implements Listener {
    public static final String ID = "spawner-player-radius";
    @art.arcane.react.util.config.ConfigDoc(value = "Required player radius used by spawner player radius (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
    private double requiredPlayerDistance = 32;
    @art.arcane.react.util.config.ConfigDoc(value = "Controls whether spawner player radius applies only monsters.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean onlyMonsters = true;
    @art.arcane.react.util.config.ConfigDoc(value = "Controls whether spawner player radius applies enforce spawner spawns.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean enforceSpawnerSpawns = true;
    @art.arcane.react.util.config.ConfigDoc(value = "Controls whether spawner player radius applies enforce trial spawner spawns.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
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
