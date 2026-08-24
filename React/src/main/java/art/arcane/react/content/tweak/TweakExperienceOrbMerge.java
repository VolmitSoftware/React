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
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Experience Orb Merge tweak. Merges nearby XP orbs into larger stacks to reduce orb entity count and pickup overhead.")
public class TweakExperienceOrbMerge extends ReactTweak implements Listener {
  public static final String ID = "experience-orb-merge";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Merge radius used by experience orb merge (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double mergeRadius = 2.75;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum nearby orbs allowed per merge in experience orb merge.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxNearbyOrbsPerMerge = 24;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum experience allowed per orb in experience orb merge.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxExperiencePerOrb = 10000;

  public TweakExperienceOrbMerge() {
    super(ID);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntitySpawnEvent event) {
    if (!(event.getEntity() instanceof ExperienceOrb orb)) {
      return;
    }

    boolean folia = J.isFoliaThreading();
    if (folia && !J.isOwnedByCurrentRegion(orb)) {
      J.runEntity(orb, () -> mergeNearbyOwned(orb));
      return;
    }

    mergeNearbyOwned(orb);
  }

  private void mergeNearbyOwned(ExperienceOrb orb) {
    if (orb.isDead()) {
      return;
    }

    int merges = 0;
    int totalExperience = Math.max(0, orb.getExperience());
    int experienceLimit = Math.max(1, maxExperiencePerOrb);
    boolean folia = J.isFoliaThreading();

    for (Entity nearby : orb.getNearbyEntities(mergeRadius, mergeRadius, mergeRadius)) {
      if (!(nearby instanceof ExperienceOrb other) || other == orb) {
        continue;
      }

      if (folia && !J.isOwnedByCurrentRegion(other)) {
        continue;
      }
      if (other.isDead() || other.getUniqueId().equals(orb.getUniqueId())) {
        continue;
      }

      int capacity = Math.max(0, experienceLimit - totalExperience);
      if (capacity == 0) {
        break;
      }

      int sourceExperience = Math.max(0, other.getExperience());
      int transferred = Math.min(sourceExperience, capacity);
      if (transferred <= 0) {
        continue;
      }

      totalExperience += transferred;
      int remainder = sourceExperience - transferred;
      if (remainder == 0) {
        other.remove();
      } else {
        other.setExperience(remainder);
      }
      merges++;

      if (merges >= Math.max(1, maxNearbyOrbsPerMerge)) {
        break;
      }
    }

    if (merges > 0) {
      orb.setExperience(Math.max(1, totalExperience));
    }
  }
}
