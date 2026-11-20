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
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ItemHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Experience Orb Merge tweak. Merges nearby XP orbs into larger stacks to reduce orb entity count and pickup overhead.")
public class TweakExperienceOrbMerge extends ReactTweak {
  public static final String ID = "experience-orb-merge";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Merge radius used by experience orb merge (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double mergeRadius = 2.75;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum nearby orbs allowed per merge in experience orb merge.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxNearbyOrbsPerMerge = 24;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum experience allowed per orb in experience orb merge.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxExperiencePerOrb = 10000;
  private transient Subscription spawns = Subscription.NONE;
  private transient volatile ItemHost items;

  public TweakExperienceOrbMerge() {
    super(ID);
  }

  @Override
  public void onActivate() {
    ItemHost host = items();
    spawns = host == null ? Subscription.NONE : host.onOrbSpawn(this::on);
  }

  @Override
  public void onDeactivate() {
    spawns.cancel();
    spawns = Subscription.NONE;
  }

  void on(GameEntity orb) {
    ItemHost host = items();
    if (host == null) {
      return;
    }

    boolean folia = J.isFoliaThreading();
    if (folia && !J.isOwnedByCurrentRegion(orb.handle())) {
      J.runEntity(orb.handle(), () -> mergeNearbyOwned(host, orb));
      return;
    }

    mergeNearbyOwned(host, orb);
  }

  private void mergeNearbyOwned(ItemHost host, GameEntity orb) {
    if (!orb.isValid()) {
      return;
    }

    int merges = 0;
    int totalExperience = Math.max(0, host.experience(orb));
    int experienceLimit = Math.max(1, maxExperiencePerOrb);
    boolean folia = J.isFoliaThreading();

    for (GameEntity other : host.nearbyOrbs(orb, mergeRadius)) {
      if (folia && !J.isOwnedByCurrentRegion(other.handle())) {
        continue;
      }
      if (!other.isValid() || other.id().equals(orb.id())) {
        continue;
      }

      int capacity = Math.max(0, experienceLimit - totalExperience);
      if (capacity == 0) {
        break;
      }

      int sourceExperience = Math.max(0, host.experience(other));
      int transferred = Math.min(sourceExperience, capacity);
      if (transferred <= 0) {
        continue;
      }

      totalExperience += transferred;
      int remainder = sourceExperience - transferred;
      if (remainder == 0) {
        host.discard(other);
      } else {
        host.setExperience(other, remainder);
      }
      merges++;

      if (merges >= Math.max(1, maxNearbyOrbsPerMerge)) {
        break;
      }
    }

    if (merges > 0) {
      host.setExperience(orb, Math.max(1, totalExperience));
    }
  }

  private ItemHost items() {
    ItemHost host = items;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.itemHost().orElse(null);
      items = host;
    }
    return host;
  }
}
