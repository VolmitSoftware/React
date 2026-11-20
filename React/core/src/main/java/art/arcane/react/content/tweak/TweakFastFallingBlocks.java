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
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Fast Falling Blocks tweak. Accelerates long-lived falling-block entities when fall processing starts causing measurable tick cost.")
public class TweakFastFallingBlocks extends ReactTweak {
  public static final String ID = "fast-falling-blocks";
  private transient ArrayDeque<FallJob> jobs;
  private transient Set<GameLocation> queued;
  private transient int ticker;
  private transient volatile BlockHost blocks;
  private transient volatile WorldHost worlds;
  private transient Subscription changes = Subscription.NONE;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum fall ms allowed by fast falling blocks.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private double maxFallMS = 1.5;

  public TweakFastFallingBlocks() {
    super(ID);
  }

  @Override
  public void onActivate() {
    jobs = new ArrayDeque<>();
    queued = new HashSet<>();
    blocks = ReactPlatforms.get().blockHost().orElse(null);
    worlds = ReactPlatforms.get().worldHost().orElse(null);
    BlockHost host = blocks;
    if (host != null) {
      changes = host.onFallingBlockChange(ListenerPriority.HIGHEST, true, this::onFallingBlockChange);
    }
    ticker = J.sr(this::drainJobs, 0);
  }

  void onFallingBlockChange(BlockHost.FallingBlockEvent e) {
    e.cancel();
    Object d = e.blockState();
    GameLocation b = e.block();
    int bonus = blocks.matchingAbove(b, d);

    synchronized (queued) {
      if (!queued.add(b)) {
        return;
      }
    }

    FallJob job = new FallJob(b, d, bonus);
    synchronized (jobs) {
      jobs.addLast(job);
    }
  }

  @Override
  public void onDeactivate() {
    changes.cancel();
    changes = Subscription.NONE;
    if (ticker != 0) {
      J.csr(ticker);
      ticker = 0;
    }

    if (jobs != null) {
      synchronized (jobs) {
        jobs.clear();
      }
    }

    if (queued != null) {
      synchronized (queued) {
        queued.clear();
      }
    }
  }

  private void drainJobs() {
    boolean folia = J.isFoliaThreading();
    PrecisionStopwatch p = PrecisionStopwatch.start();
    while (p.getMilliseconds() < maxFallMS) {
      FallJob job;
      synchronized (jobs) {
        job = jobs.pollFirst();
      }
      if (job == null) {
        return;
      }

      if (folia) {
        WorldHost presence = worlds;
        ReactWorld world = presence == null ? null : presence.world(job.block().worldId()).orElse(null);
        J.runChunk(world, job.block().chunkX(), job.block().chunkZ(), () -> applyFall(job), 0);
      } else {
        applyFall(job);
      }
    }
  }

  private void applyFall(FallJob job) {
    try {
      blocks.dropColumn(job.block(), job.data(), job.bonus());
    } finally {
      synchronized (queued) {
        queued.remove(job.block());
      }
    }
  }

  private record FallJob(GameLocation block, Object data, int bonus) {
  }
}
