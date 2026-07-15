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
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.volmlib.util.format.Form;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Incident Playbook action. Queues a tiered set of mitigation actions based on current incident severity.")
public class ActionIncidentPlaybook extends ReactAction<ActionIncidentPlaybook.Params> {
  public static final String ID = "action-incident-playbook";

  public ActionIncidentPlaybook() {
    super(ID);
  }

  @Override
  public String getCompletedMessage(ActionTicket<Params> ticket) {
    Params p = ticket.getParams();
    return "Playbook tier " + p.getTier() + " queued " + p.getQueuedActions() + " mitigation actions in " + Form.duration(ticket.getDuration(), 1);
  }

  @Override
  public void workOn(ActionTicket<Params> ticket) {
    Params params = ticket.getParams();
    if (params.isPrepared()) {
      ticket.complete();
      return;
    }

    params.setPrepared(true);
    params.setIncidentScore(sample(SamplerIncidentScore.ID));
    params.setTickMS(sample(SamplerTickTime.ID));
    int tier = params.getTierOverride() >= 0 ? Math.max(0, Math.min(2, params.getTierOverride())) : inferTier(params.getIncidentScore(), params.getTickMS());
    params.setTier(tier);

    int queued = 0;
    queued += queueQuarantine(params, tier) ? 1 : 0;
    queued += queueTrim(params, tier) ? 1 : 0;
    queued += queueHopperNormalize(params, tier) ? 1 : 0;
    queued += queuePrewarm(params, tier) ? 1 : 0;
    if (params.isIncludeGarbageCollection()) {
      queued += queueAction(ActionCollectGarbage.ID, ActionCollectGarbage.Params.builder().build()) ? 1 : 0;
    }

    params.setQueuedActions(queued);
    ticket.setCount(queued);
    ticket.complete();
  }

  @Override
  public Params getDefaultParams() {
    return Params.builder().build();
  }

  @Override
  public void onInit() {

  }

  private boolean queueQuarantine(Params params, int tier) {
    ActionQuarantineHotChunks.Params quarantine = ActionQuarantineHotChunks.Params.builder().build();
    if (params.getWorld() != null && !params.getWorld().isBlank()) {
      quarantine.setWorld(params.getWorld());
    }

    if (tier == 0) {
      quarantine.setMaxChunks(16).setMinimumChunkScore(100D).setUnsafePlayerRadius(64D);
    } else if (tier == 1) {
      quarantine.setMaxChunks(28).setMinimumChunkScore(80D).setUnsafePlayerRadius(56D);
    } else {
      quarantine.setMaxChunks(42).setMinimumChunkScore(60D).setUnsafePlayerRadius(48D);
    }

    return queueAction(ActionQuarantineHotChunks.ID, quarantine);
  }

  private boolean queueTrim(Params params, int tier) {
    ActionTrimEntitiesByAgePriority.Params trim = ActionTrimEntitiesByAgePriority.Params.builder().build();
    if (params.getWorld() != null && !params.getWorld().isBlank()) {
      trim.setWorld(params.getWorld());
    }

    if (tier == 0) {
      trim.setMaxTrim(300).setMaxTrimPerChunk(8).setMinEntityAgeTicks(20 * 60 * 8);
    } else if (tier == 1) {
      trim.setMaxTrim(600).setMaxTrimPerChunk(12).setMinEntityAgeTicks(20 * 60 * 5);
    } else {
      trim.setMaxTrim(1000).setMaxTrimPerChunk(16).setMinEntityAgeTicks(20 * 60 * 3);
    }

    return queueAction(ActionTrimEntitiesByAgePriority.ID, trim);
  }

  private boolean queueHopperNormalize(Params params, int tier) {
    ActionHopperNetworkNormalize.Params normalize = ActionHopperNetworkNormalize.Params.builder().build();
    if (params.getWorld() != null && !params.getWorld().isBlank()) {
      normalize.setWorld(params.getWorld());
    }

    if (tier == 0) {
      normalize.setMaxChunks(12).setMinimumHopperUpdatesPerChunk(30D).setMaxMergedItemEntitiesPerChunk(36);
    } else if (tier == 1) {
      normalize.setMaxChunks(20).setMinimumHopperUpdatesPerChunk(25D).setMaxMergedItemEntitiesPerChunk(48);
    } else {
      normalize.setMaxChunks(32).setMinimumHopperUpdatesPerChunk(18D).setMaxMergedItemEntitiesPerChunk(64);
    }

    return queueAction(ActionHopperNetworkNormalize.ID, normalize);
  }

  private boolean queuePrewarm(Params params, int tier) {
    ActionPrewarmCriticalChunks.Params prewarm = ActionPrewarmCriticalChunks.Params.builder().build();
    if (params.getWorld() != null && !params.getWorld().isBlank()) {
      prewarm.setWorld(params.getWorld());
    }

    if (tier == 0) {
      prewarm.setMaxChunks(20).setNeighborRadius(1).setPlayerChunkRadius(1);
    } else if (tier == 1) {
      prewarm.setMaxChunks(32).setNeighborRadius(1).setPlayerChunkRadius(1);
    } else {
      prewarm.setMaxChunks(48).setNeighborRadius(2).setPlayerChunkRadius(2);
    }

    return queueAction(ActionPrewarmCriticalChunks.ID, prewarm);
  }

  private boolean queueAction(String id, ActionParams params) {
    Action<?> action = React.action(id);
    if (action == null) {
      React.warn("Incident playbook missing action: " + id);
      return false;
    }

    try {
      action.createForceful(params).queue();
      return true;
    } catch (Throwable e) {
      React.warn("Incident playbook failed to queue action " + id + ": " + e.getMessage());
      return false;
    }
  }

  private int inferTier(double incident, double tickMS) {
    if (incident >= 70D || tickMS >= 75D) {
      return 2;
    }

    if (incident >= 45D || tickMS >= 58D) {
      return 1;
    }

    return 0;
  }

  private double sample(String samplerId) {
    Sampler sampler = React.sampler(samplerId);
    return sampler == null ? 0D : sampler.sample();
  }

  @Builder
  @Data
  @Accessors(chain = true)
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Params implements ActionParams {
    @art.arcane.react.util.project.config.ConfigDoc(value = "World name filter for incident playbook operations.", impact = "Set a world name to scope actions there, or leave blank to include all worlds.")
    private String world;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Includes garbage collection in incident playbook processing.", impact = "Enable this to add those targets to processing; disable it to leave them out.")
    private boolean includeGarbageCollection = true;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Override tier used by incident playbook when selecting mitigation intensity.", impact = "Higher tiers generally choose more aggressive responses; lower tiers keep interventions lighter.")
    private int tierOverride = -1;
    @Builder.Default
    private transient boolean prepared = false;
    @Builder.Default
    private transient int queuedActions = 0;
    @Builder.Default
    private transient int tier = 0;
    @Builder.Default
    private transient double incidentScore = 0D;
    @Builder.Default
    private transient double tickMS = 0D;
  }
}
