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

package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.util.scheduling.J;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SamplerEntityAiActiveCount extends ReactCachedSampler {
  public static final String ID = "entity-ai-active-count";

  public SamplerEntityAiActiveCount() {
    super(ID, 2000);
  }

  @Override
  public Material getIcon() {
    return Material.ZOMBIE_SPAWN_EGG;
  }

  @Override
  public double onSample() {
    if (J.isFoliaThreading()) {
      return sampleFoliaApproximation();
    }

    return executeSync(() -> {
      int active = 0;
      for (World world : Bukkit.getWorlds()) {
        for (Entity entity : world.getEntities()) {
          if (entity instanceof LivingEntity living && !(entity instanceof Player) && !living.isDead() && living.hasAI()) {
            active++;
          }
        }
      }

      return (double) active;
    });
  }

  private double sampleFoliaApproximation() {
    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
    if (players.isEmpty()) {
      return 0D;
    }

    AtomicInteger active = new AtomicInteger();
    Set<UUID> seen = ConcurrentHashMap.newKeySet();
    CountDownLatch latch = new CountDownLatch(players.size());

    for (Player player : players) {
      boolean scheduled = J.runEntity(player, () -> {
        try {
          if (player == null || !player.isOnline() || !J.isOwnedByCurrentRegion(player)) {
            return;
          }

          for (Entity entity : player.getNearbyEntities(80, 48, 80)) {
            if (!(entity instanceof LivingEntity living) || entity instanceof Player || living.isDead() || !living.hasAI()) {
              continue;
            }

            if (seen.add(entity.getUniqueId())) {
              active.incrementAndGet();
            }
          }
        } finally {
          latch.countDown();
        }
      });

      if (!scheduled) {
        latch.countDown();
      }
    }

    try {
      latch.await(200, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      React.verbose("SamplerEntityAiActiveCount wait interrupted while gathering Folia approximation.");
    }

    return active.get();
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(Math.round(t));
  }

  @Override
  public String formattedSuffix(double t) {
    return "AI";
  }
}
