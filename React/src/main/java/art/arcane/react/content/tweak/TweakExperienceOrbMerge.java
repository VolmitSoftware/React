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

import art.arcane.react.api.tweak.ReactTweak;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

@art.arcane.react.util.config.ConfigDescription("Configuration for Experience Orb Merge tweak. Merges nearby XP orbs into larger stacks to reduce orb entity count and pickup overhead.")
public class TweakExperienceOrbMerge extends ReactTweak implements Listener {
    public static final String ID = "experience-orb-merge";
    @art.arcane.react.util.config.ConfigDoc(value = "Merge radius used by experience orb merge (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
    private double mergeRadius = 2.75;
    @art.arcane.react.util.config.ConfigDoc(value = "Maximum nearby orbs allowed per merge in experience orb merge.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxNearbyOrbsPerMerge = 24;
    @art.arcane.react.util.config.ConfigDoc(value = "Maximum experience allowed per orb in experience orb merge.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxExperiencePerOrb = 10000;

    public TweakExperienceOrbMerge() {
        super(ID);
    }

    @Override
    public void onActivate() {

    }

    @Override
    public void onDeactivate() {

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof ExperienceOrb orb) || orb.isDead()) {
            return;
        }

        int merges = 0;
        int totalExperience = orb.getExperience();

        for (Entity nearby : orb.getNearbyEntities(mergeRadius, mergeRadius, mergeRadius)) {
            if (nearby.isDead() || nearby.getUniqueId().equals(orb.getUniqueId())) {
                continue;
            }

            if (nearby instanceof ExperienceOrb other) {
                totalExperience += Math.max(0, other.getExperience());
                other.remove();
                merges++;

                if (merges >= maxNearbyOrbsPerMerge) {
                    break;
                }
            }
        }

        if (merges > 0) {
            orb.setExperience(Math.min(maxExperiencePerOrb, Math.max(1, totalExperience)));
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
