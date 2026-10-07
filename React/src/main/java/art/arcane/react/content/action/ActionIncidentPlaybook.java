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

package art.arcane.react.content.action;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.action.ReactAction;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.content.sampler.SamplerGcTimePercent;
import art.arcane.react.content.sampler.SamplerHopperUpdates;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerMemoryGarbage;
import art.arcane.react.content.sampler.SamplerMemoryUsed;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.ActionMessages;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.model.SampledWorld;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@ConfigDescription("Runs relevant incident mitigations one at a time, observes pressure between actions, and completes after their terminal outcomes.")
public class ActionIncidentPlaybook extends ReactAction<ActionIncidentPlaybook.Params> {
  public static final String ID = "action-incident-playbook";

  public ActionIncidentPlaybook() {
    super(ID);
  }

  @Override
  public String getCompletedMessage(ActionTicket<Params> ticket) {
    Params params = ticket.getParams();
    return ReactLanguage.plain(
        ActionMessages.PLAYBOOK_COMPLETED,
        MessageArgument.untrusted("tier", params.getTier()),
        MessageArgument.untrusted("actions", params.getCompletedActions()),
        MessageArgument.untrusted("duration", Form.duration(ticket.getDuration(), 1))
    );
  }

  @Override
  public void workOn(ActionTicket<Params> ticket) {
    if (ticket.isDone()) {
      return;
    }
    Params params = ticket.getParams();
    if (!params.isPrepared()) {
      params.setPrepared(true);
      ticket.onTerminal(this::stopChild);
      ticket.setTotalWork(Mitigation.values().length);
    }
    if (params.getActiveChild() != null) {
      advanceChild(ticket, params);
      return;
    }
    if (System.nanoTime() < params.getNextEvaluationNanos()) {
      return;
    }

    params.setIncidentScore(sample(SamplerIncidentScore.ID));
    params.setTickMS(sample(SamplerTickTime.ID));
    if (!observationReady(params)) {
      return;
    }
    params.setTier(params.getTierOverride() >= 0
        ? Math.min(2, params.getTierOverride())
        : inferTier(params.getIncidentScore(), params.getTickMS()));
    if (!(params.getIncidentScore() >= Math.max(0D, params.getMinimumIncidentScore()))
        && !(params.getTickMS() >= Math.max(1D, params.getMinimumTickMS()))) {
      ticket.setWork(ticket.getTotalWork());
      ticket.complete();
      return;
    }

    Choice choice = choose(params);
    if (choice == null) {
      ticket.setWork(ticket.getTotalWork());
      ticket.complete();
      return;
    }
    params.getAttempted().add(choice.mitigation());
    Action<?> action = React.action(choice.mitigation().actionId);
    if (action == null || !action.isEnabled()) {
      return;
    }
    ActionTicket<?> child = action.createForceful(childParams(choice, params));
    params.setActiveChild(child);
    params.setChildStartedNanos(System.nanoTime());
    if (ticket.isDone()) {
      stopChild(ticket);
      return;
    }
    try {
      child.start();
    } catch (Throwable failure) {
      child.fail(failure);
      ticket.fail(new IllegalStateException("Incident mitigation could not start: " + action.getId(), failure));
      React.reportError(failure);
    }
  }

  @Override
  public Params getDefaultParams() {
    return Params.builder().build();
  }

  @Override
  public void onInit() {
  }

  private void advanceChild(ActionTicket<Params> ticket, Params params) {
    ActionTicket<?> child = params.getActiveChild();
    if (!child.isDone()) {
      long timeout = TimeUnit.MILLISECONDS.toNanos(Math.max(1000L, params.getChildTimeoutMS()));
      if (System.nanoTime() - params.getChildStartedNanos() >= timeout) {
        child.fail(new IllegalStateException("Incident mitigation timed out: " + child.getAction().getId()));
      } else if (!child.getAction().isEnabled()) {
        child.fail(new IllegalStateException("Incident mitigation was disabled: " + child.getAction().getId()));
      } else {
        try {
          driveChild(child);
        } catch (Throwable failure) {
          child.fail(failure);
          React.reportError(failure);
        }
      }
    }
    if (!child.isDone()) {
      return;
    }
    if (child.isFailed()) {
      ticket.fail(new IllegalStateException("Incident mitigation failed: " + child.getAction().getId(), child.getFailure()));
      return;
    }
    params.setCompletedActions(params.getCompletedActions() + 1);
    ticket.setCount(params.getCompletedActions());
    ticket.setWork(params.getAttempted().size());
    params.setActiveChild(null);
    params.setLastChildCompletedMS(System.currentTimeMillis());
    sample(SamplerIncidentScore.ID);
    sample(SamplerTickTime.ID);
    params.setNextEvaluationNanos(System.nanoTime()
        + TimeUnit.MILLISECONDS.toNanos(Math.max(1000L, params.getRecheckDelayMS())));
  }

  private boolean observationReady(Params params) {
    if (params.getLastChildCompletedMS() == 0L) {
      return true;
    }
    Sampler sampler = React.sampler(SamplerIncidentScore.ID);
    if (sampler instanceof SamplerIncidentScore incident && incident.isSampleAvailable()) {
      if (incident.snapshot().sampledAtMs() <= params.getLastChildCompletedMS()) {
        long elapsed = System.currentTimeMillis() - params.getLastChildCompletedMS();
        if (elapsed >= Math.max(1000L, params.getChildTimeoutMS())) {
          throw new IllegalStateException("Incident telemetry did not refresh after mitigation");
        }
        return false;
      }
    }
    return true;
  }

  private <T extends ActionParams> void driveChild(ActionTicket<T> child) {
    child.getAction().workOn(child);
  }

  private void stopChild(ActionTicket<Params> parent) {
    ActionTicket<?> child = parent.getParams().getActiveChild();
    if (child != null && !child.isDone()) {
      child.fail(new IllegalStateException("Incident playbook stopped before mitigation completed", parent.getFailure()));
    }
  }

  private Choice choose(Params params) {
    ObserverController observer = React.controller(ObserverController.class);
    String hopperWorld = null;
    String entityWorld = null;
    String chunkWorld = null;
    double hopperMaximum = 0D;
    double entityMaximum = 0D;
    double chunkMaximum = 0D;
    if (observer != null && observer.getSampled() != null) {
      for (SampledWorld world : observer.getSampled().getWorlds().values()) {
        String worldKey = world.getWorldKey();
        if (worldKey == null || (params.getWorld() != null && !params.getWorld().isBlank()
            && !params.getWorld().equals(worldKey))) {
          continue;
        }
        for (SampledChunk chunk : world.getChunks().values()) {
          double hoppers = chunk.optional(SamplerHopperUpdates.ID).map(value -> value.get()).orElse(0D);
          double entities = chunk.optional(SamplerEntities.ID).map(value -> value.get()).orElse(0D);
          double score = chunk.totalScore();
          if (hoppers > hopperMaximum) {
            hopperMaximum = hoppers;
            hopperWorld = worldKey;
          }
          if (entities > entityMaximum) {
            entityMaximum = entities;
            entityWorld = worldKey;
          }
          if (score > chunkMaximum) {
            chunkMaximum = score;
            chunkWorld = worldKey;
          }
        }
      }
    }
    if (!params.getAttempted().contains(Mitigation.HOPPERS)
        && hopperWorld != null && hopperMaximum >= hopperThreshold(params.getTier())) {
      return new Choice(Mitigation.HOPPERS, hopperWorld);
    }
    if (!params.getAttempted().contains(Mitigation.ENTITIES)
        && entityWorld != null && entityMaximum >= Math.max(1, params.getMinimumEntitiesPerChunk())) {
      return new Choice(Mitigation.ENTITIES, entityWorld);
    }
    if (!params.getAttempted().contains(Mitigation.CHUNKS)
        && chunkWorld != null && chunkMaximum >= chunkThreshold(params.getTier())) {
      return new Choice(Mitigation.CHUNKS, chunkWorld);
    }
    if (params.isIncludeGarbageCollection() && !params.getAttempted().contains(Mitigation.GARBAGE)
        && garbageCollectionRelevant()) {
      return new Choice(Mitigation.GARBAGE, null);
    }
    return null;
  }

  private ActionParams childParams(Choice choice, Params params) {
    int tier = params.getTier();
    return switch (choice.mitigation()) {
      case HOPPERS -> ActionHopperNetworkNormalize.Params.builder().build()
          .setWorld(choice.world())
          .setMaxChunks(tier == 0 ? 12 : tier == 1 ? 20 : 32)
          .setMinimumHopperUpdatesPerChunk(hopperThreshold(tier))
          .setMaxMergedItemEntitiesPerChunk(tier == 0 ? 36 : tier == 1 ? 48 : 64)
          .setUnloadIdleHotChunks(false);
      case ENTITIES -> ActionTrimEntitiesByAgePriority.Params.builder().build()
          .setWorld(choice.world())
          .setMaxTrim(tier == 0 ? 300 : tier == 1 ? 600 : 1000)
          .setMaxTrimPerChunk(tier == 0 ? 8 : tier == 1 ? 12 : 16)
          .setMinimumEntitiesPerChunk(Math.max(1, params.getMinimumEntitiesPerChunk()))
          .setMinEntityAgeTicks(20 * 60 * (tier == 0 ? 8 : tier == 1 ? 5 : 3));
      case CHUNKS -> ActionQuarantineHotChunks.Params.builder().build()
          .setWorld(choice.world())
          .setMaxChunks(tier == 0 ? 16 : tier == 1 ? 28 : 42)
          .setMinimumChunkScore(chunkThreshold(tier))
          .setUnsafePlayerRadius(tier == 0 ? 64D : tier == 1 ? 56D : 48D)
          .setCullEntities(false)
          .setIncludeNeighborRing(false);
      case GARBAGE -> ActionCollectGarbage.Params.builder().build();
    };
  }

  private boolean garbageCollectionRelevant() {
    long maximumHeap = Runtime.getRuntime().maxMemory();
    return maximumHeap > 0L
        && sample(SamplerMemoryUsed.ID) >= maximumHeap * 0.9D
        && sample(SamplerMemoryGarbage.ID) >= maximumHeap * 0.1D
        && sample(SamplerGcTimePercent.ID) < 2D;
  }

  private double hopperThreshold(int tier) {
    return tier == 0 ? 30D : tier == 1 ? 25D : 18D;
  }

  private double chunkThreshold(int tier) {
    return tier == 0 ? 100D : tier == 1 ? 80D : 60D;
  }

  private int inferTier(double incident, double tickMS) {
    return incident >= 70D || tickMS >= 75D ? 2 : incident >= 45D || tickMS >= 58D ? 1 : 0;
  }

  private double sample(String samplerId) {
    Sampler sampler = React.sampler(samplerId);
    if (sampler == null) {
      return Double.NaN;
    }
    double value = sampler.sample();
    return sampler.isSampleAvailable() && Double.isFinite(value) ? value : Double.NaN;
  }

  private enum Mitigation {
    HOPPERS(ActionHopperNetworkNormalize.ID),
    ENTITIES(ActionTrimEntitiesByAgePriority.ID),
    CHUNKS(ActionQuarantineHotChunks.ID),
    GARBAGE(ActionCollectGarbage.ID);

    private final String actionId;

    Mitigation(String actionId) {
      this.actionId = actionId;
    }
  }

  private record Choice(Mitigation mitigation, String world) {
  }

  @Builder
  @Data
  @Accessors(chain = true)
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Params implements ActionParams {
    @ConfigDoc(value = "World key filter for incident playbook operations.", impact = "Leave blank to select the world with the strongest evidence for each mitigation.")
    private String world;
    @Builder.Default
    @ConfigDoc(value = "Permits a garbage collection stage when heap use exceeds 90%, estimated reclaimable heap exceeds 10%, and GC time stays below 2%.", impact = "Disabled by default because explicit collection can pause the server.")
    private boolean includeGarbageCollection = false;
    @Builder.Default
    @ConfigDoc(value = "Mitigation intensity override from 0 to 2; -1 chooses from observed pressure.", impact = "Overrides intensity only; relevant evidence and sustained pressure remain required.")
    private int tierOverride = -1;
    @Builder.Default
    @ConfigDoc(value = "Incident score required for the playbook to continue mitigation.", impact = "The playbook continues when this score or minimumTickMS is reached.")
    private double minimumIncidentScore = 35D;
    @Builder.Default
    @ConfigDoc(value = "Average tick milliseconds required for the playbook to continue mitigation.", impact = "The playbook stops when both pressure thresholds are below their limits.")
    private double minimumTickMS = 48D;
    @Builder.Default
    @ConfigDoc(value = "Observed entities in one chunk required before the entity trimming stage.", impact = "Higher values restrict trimming to denser entity hotspots.")
    private int minimumEntitiesPerChunk = 80;
    @Builder.Default
    @ConfigDoc(value = "Observation interval after a child action completes, in milliseconds; minimum 1000.", impact = "Allows metrics to refresh before selecting another mitigation.")
    private long recheckDelayMS = 2000L;
    @Builder.Default
    @ConfigDoc(value = "Maximum runtime of one mitigation stage, in milliseconds; minimum 1000.", impact = "A timed-out child fails the playbook and stops further escalation.")
    private long childTimeoutMS = 60000L;
    @Builder.Default
    private transient boolean prepared = false;
    @Builder.Default
    private transient int completedActions = 0;
    @Builder.Default
    private transient int tier = 0;
    private transient double incidentScore;
    private transient double tickMS;
    private transient long nextEvaluationNanos;
    private transient long childStartedNanos;
    private transient long lastChildCompletedMS;
    private transient volatile ActionTicket<?> activeChild;
    @Builder.Default
    private transient Set<Mitigation> attempted = EnumSet.noneOf(Mitigation.class);
  }
}
