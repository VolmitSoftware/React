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
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.SpawnHost;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Spawner Player Radius tweak. Blocks spawner and trial-spawner spawns when players are outside the required activation distance.")
public class TweakSpawnerPlayerRadius extends ReactTweak {
  public static final String ID = "spawner-player-radius";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Required player radius used by spawner player radius (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double requiredPlayerDistance = 32;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether spawner player radius applies only monsters.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean onlyMonsters = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether spawner player radius applies enforce spawner spawns.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean enforceSpawnerSpawns = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether spawner player radius applies enforce trial spawner spawns.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean enforceTrialSpawnerSpawns = true;
  private transient volatile WorldHost worlds;
  private transient Subscription subscription = Subscription.NONE;

  public TweakSpawnerPlayerRadius() {
    super(ID);
  }

  @Override
  public void onActivate() {
    ReactPlatform platform = ReactPlatforms.get();
    worlds = platform.worldHost().orElse(null);
    subscription = platform.spawnHost()
        .map(host -> host.onCreatureSpawn(ListenerPriority.LOW, true, this::on))
        .orElse(Subscription.NONE);
  }

  @Override
  public void onDeactivate() {
    subscription.cancel();
    subscription = Subscription.NONE;
  }

  void on(SpawnHost.Spawn event) {
    String reason = event.reason();
    boolean spawnerReason = ("SPAWNER".equals(reason) && enforceSpawnerSpawns)
        || ("TRIAL_SPAWNER".equals(reason) && enforceTrialSpawnerSpawns);

    if (!spawnerReason) {
      return;
    }

    if (onlyMonsters && !event.isMonster()) {
      return;
    }

    WorldHost presence = worlds;
    if (presence == null || !presence.anyPlayerNear(event.location(), requiredPlayerDistance)) {
      event.cancel();
    }
  }
}
