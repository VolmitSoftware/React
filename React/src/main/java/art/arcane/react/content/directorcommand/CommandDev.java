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

package art.arcane.react.content.directorcommand;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.content.action.ActionCollectGarbage;
import art.arcane.react.content.action.ActionHopperNetworkNormalize;
import art.arcane.react.content.action.ActionIncidentPlaybook;
import art.arcane.react.content.action.ActionPrewarmCriticalChunks;
import art.arcane.react.content.action.ActionPurgeChunks;
import art.arcane.react.content.action.ActionPurgeEntities;
import art.arcane.react.content.action.ActionQuarantineHotChunks;
import art.arcane.react.content.action.ActionTrimEntitiesByAgePriority;
import art.arcane.react.content.action.ActionUnknown;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.TweakController;
import art.arcane.react.model.AreaActionParams;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.format.C;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.react.util.project.registry.Registry;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Director(
    name = "dev",
    aliases = {"developer", "d"},
    origin = DirectorOrigin.BOTH,
    description = "Developer-only React validation and diagnostics."
)
public class CommandDev implements DirectorExecutor {
  private static final List<String> TEST_ACTION_ORDER = List.of(
      ActionCollectGarbage.ID,
      ActionPrewarmCriticalChunks.ID,
      ActionQuarantineHotChunks.ID,
      ActionHopperNetworkNormalize.ID,
      ActionTrimEntitiesByAgePriority.ID,
      ActionPurgeEntities.ID,
      ActionPurgeChunks.ID
  );
  private static final Set<String> EXCLUDED_ACTION_IDS = Set.of(
      ActionIncidentPlaybook.ID,
      ActionUnknown.ID
  );

  @Director(
      name = "test-all",
      aliases = {"ta"},
      origin = DirectorOrigin.PLAYER,
      sync = true,
      description = "Audit React state, then queue the direct action suite one step at a time in your current world."
  )
  public void testAll(
      @Param(
          name = "radius",
          description = "Chunk radius used for the local purge entity/chunk test steps.",
          defaultValue = "2",
          aliases = {"r"}
      )
      int radius
  ) {
    VolmitSender commandSender = sender();
    Player executingPlayer = player();
    World world = executingPlayer.getWorld();
    int safeRadius = Math.max(0, Math.min(radius, 6));
    ActionController actionController = React.controller(ActionController.class);
    if (actionController == null || actionController.getActions() == null) {
      commandSender.sendMessage(C.REACT + "Action controller is unavailable.");
      return;
    }

    sendRuntimeAudit(commandSender, actionController);

    List<ActionTestStep> steps = buildTestSteps(actionController, world, executingPlayer, safeRadius);
    if (steps.isEmpty()) {
      commandSender.sendMessage(C.REACT + "No enabled direct actions are available for the dev test suite.");
      return;
    }

    List<String> uncoveredActions = findUncoveredEnabledActions(actionController);
    commandSender.sendMessage(C.REACT + "Dev suite scope: world=" + world.getName() + ", purgeRadius=" + safeRadius + " chunks.");
    commandSender.sendMessage(C.REACT + "Queueing " + steps.size() + " direct actions sequentially. Recursive meta actions stay excluded.");
    if (!uncoveredActions.isEmpty()) {
      commandSender.sendMessage(C.REACT + "Enabled actions not covered by this suite: " + String.join(", ", uncoveredActions));
    }

    queueStep(commandSender, steps, 0);
  }

  private void sendRuntimeAudit(VolmitSender commandSender, ActionController actionController) {
    FeatureController featureController = React.controller(FeatureController.class);
    TweakController tweakController = React.controller(TweakController.class);
    SampleController sampleController = React.controller(SampleController.class);

    int registeredFeatures = size(featureController == null ? null : featureController.getFeatures());
    int enabledFeatures = countEnabledFeatures(featureController == null ? null : featureController.getFeatures());
    int activeFeatures = featureController == null || featureController.getActiveFeatures() == null ? 0 : featureController.getActiveFeatures().size();

    int registeredTweaks = size(tweakController == null ? null : tweakController.getTweaks());
    int enabledTweaks = countEnabledTweaks(tweakController == null ? null : tweakController.getTweaks());
    int activeTweaks = tweakController == null || tweakController.getActiveTweaks() == null ? 0 : tweakController.getActiveTweaks().size();

    int registeredSamplers = size(sampleController == null ? null : sampleController.getSamplers());
    int registeredActions = size(actionController.getActions());
    int enabledActions = countEnabledActions(actionController.getActions());

    commandSender.sendMessage(C.REACT + "React dev audit:");
    commandSender.sendMessage(C.REACT + "- Features: " + registeredFeatures + " registered, " + enabledFeatures + " enabled, " + activeFeatures + " active");
    commandSender.sendMessage(C.REACT + "- Tweaks: " + registeredTweaks + " registered, " + enabledTweaks + " enabled, " + activeTweaks + " active");
    commandSender.sendMessage(C.REACT + "- Samplers: " + registeredSamplers + " registered");
    commandSender.sendMessage(C.REACT + "- Actions: " + registeredActions + " registered, " + enabledActions + " enabled");
    commandSender.sendMessage(C.REACT + "- Excluded from dev suite: " + ActionIncidentPlaybook.ID + " (recursive meta action), " + ActionUnknown.ID);
  }

  private List<ActionTestStep> buildTestSteps(ActionController actionController, World world, Player executingPlayer, int radius) {
    List<ActionTestStep> steps = new ArrayList<>();
    Registry<Action<?>> registry = actionController.getActions();
    if (registry == null) {
      return steps;
    }

    for (String actionId : TEST_ACTION_ORDER) {
      Action<?> action = registry.get(actionId);
      if (action == null || !action.isEnabled()) {
        continue;
      }

      ActionParams params = prepareParams(actionId, action.getDefaultParams(), world, executingPlayer, radius);
      steps.add(new ActionTestStep(action, params));
    }

    return steps;
  }

  private List<String> findUncoveredEnabledActions(ActionController actionController) {
    List<String> uncovered = new ArrayList<>();
    Registry<Action<?>> registry = actionController.getActions();
    if (registry == null) {
      return uncovered;
    }

    Set<String> covered = new HashSet<>(TEST_ACTION_ORDER);
    List<Action<?>> actions = new ArrayList<>(registry.all());
    actions.sort(Comparator.comparing(Action::getId));
    for (Action<?> action : actions) {
      if (action == null || !action.isEnabled()) {
        continue;
      }

      if (covered.contains(action.getId()) || EXCLUDED_ACTION_IDS.contains(action.getId())) {
        continue;
      }

      uncovered.add(action.getId());
    }

    return uncovered;
  }

  private ActionParams prepareParams(String actionId, ActionParams params, World world, Player executingPlayer, int radius) {
    if (ActionPurgeEntities.ID.equals(actionId) && params instanceof ActionPurgeEntities.Params purgeEntitiesParams) {
      AreaActionParams area = purgeEntitiesParams.getArea();
      if (area == null) {
        area = AreaActionParams.builder().build();
        purgeEntitiesParams.setArea(area);
      }

      area.setWorld(world.getName());
      area.setChunks(collectLoadedChunks(world, executingPlayer.getLocation().getChunk().getX(), executingPlayer.getLocation().getChunk().getZ(), radius));
      area.setAllChunks(false);
      return purgeEntitiesParams;
    }

    if (ActionPurgeChunks.ID.equals(actionId) && params instanceof ActionPurgeChunks.Params purgeChunksParams) {
      AreaActionParams area = purgeChunksParams.getArea();
      if (area == null) {
        area = AreaActionParams.builder().build();
        purgeChunksParams.setArea(area);
      }

      area.setWorld(world.getName());
      area.setChunks(collectLoadedChunks(world, executingPlayer.getLocation().getChunk().getX(), executingPlayer.getLocation().getChunk().getZ(), radius));
      area.setAllChunks(false);
      return purgeChunksParams;
    }

    if (ActionQuarantineHotChunks.ID.equals(actionId) && params instanceof ActionQuarantineHotChunks.Params quarantineParams) {
      quarantineParams.withWorld(world);
      quarantineParams.setMaxChunks(Math.min(quarantineParams.getMaxChunks(), 8));
      return quarantineParams;
    }

    if (ActionHopperNetworkNormalize.ID.equals(actionId) && params instanceof ActionHopperNetworkNormalize.Params normalizeParams) {
      normalizeParams.withWorld(world);
      normalizeParams.setMaxChunks(Math.min(normalizeParams.getMaxChunks(), 8));
      return normalizeParams;
    }

    if (ActionTrimEntitiesByAgePriority.ID.equals(actionId) && params instanceof ActionTrimEntitiesByAgePriority.Params trimParams) {
      trimParams.withWorld(world);
      trimParams.setMaxTrim(Math.min(trimParams.getMaxTrim(), 128));
      trimParams.setMaxTrimPerChunk(Math.min(trimParams.getMaxTrimPerChunk(), 4));
      return trimParams;
    }

    if (ActionPrewarmCriticalChunks.ID.equals(actionId) && params instanceof ActionPrewarmCriticalChunks.Params prewarmParams) {
      prewarmParams.withWorld(world);
      prewarmParams.setMaxChunks(Math.min(prewarmParams.getMaxChunks(), 8));
      return prewarmParams;
    }

    return params;
  }

  private List<Chunk> collectLoadedChunks(World world, int centerX, int centerZ, int radius) {
    List<Chunk> chunks = new ArrayList<>();
    for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
      for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          continue;
        }

        chunks.add(world.getChunkAt(chunkX, chunkZ));
      }
    }

    if (chunks.isEmpty() && world.isChunkLoaded(centerX, centerZ)) {
      chunks.add(world.getChunkAt(centerX, centerZ));
    }

    return chunks;
  }

  private void queueStep(VolmitSender commandSender, List<ActionTestStep> steps, int index) {
    if (index >= steps.size()) {
      commandSender.sendMessage(C.REACT + "React dev test suite finished.");
      return;
    }

    ActionTestStep step = steps.get(index);
    Action<?> action = step.action();
    if (action == null || !action.isEnabled()) {
      commandSender.sendMessage(C.REACT + "[" + (index + 1) + "/" + steps.size() + "] Skipped " + (action == null ? "unknown-action" : action.getId()) + " because it is unavailable.");
      queueStep(commandSender, steps, index + 1);
      return;
    }

    ActionTicket<?> ticket;
    try {
      ticket = action.createForceful(step.params());
    } catch (Throwable e) {
      commandSender.sendMessage(C.REACT + "[" + (index + 1) + "/" + steps.size() + "] Failed to create " + action.getId() + ": " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage()));
      React.reportError(e);
      queueStep(commandSender, steps, index + 1);
      return;
    }

    Action<?> queuedAction = action;
    ticket.onStart((started) -> commandSender.sendMessage(C.REACT + "[" + (index + 1) + "/" + steps.size() + "] Starting " + queuedAction.getId()))
        .onComplete((completed) -> {
          commandSender.sendMessage(C.REACT + "[" + (index + 1) + "/" + steps.size() + "] " + completedMessage(queuedAction, completed));
          queueStep(commandSender, steps, index + 1);
        });

    commandSender.sendMessage(C.REACT + "[" + (index + 1) + "/" + steps.size() + "] Queued " + action.getId());
    ticket.queue();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private String completedMessage(Action<?> action, ActionTicket<?> ticket) {
    return ((Action) action).getCompletedMessage((ActionTicket) ticket);
  }

  private int size(Registry<?> registry) {
    return registry == null ? 0 : registry.size();
  }

  private int countEnabledFeatures(Registry<Feature> registry) {
    if (registry == null) {
      return 0;
    }

    int enabled = 0;
    for (Feature feature : registry.all()) {
      if (feature != null && feature.isEnabled()) {
        enabled++;
      }
    }
    return enabled;
  }

  private int countEnabledTweaks(Registry<Tweak> registry) {
    if (registry == null) {
      return 0;
    }

    int enabled = 0;
    for (Tweak tweak : registry.all()) {
      if (tweak != null && tweak.isEnabled()) {
        enabled++;
      }
    }
    return enabled;
  }

  private int countEnabledActions(Registry<Action<?>> registry) {
    if (registry == null) {
      return 0;
    }

    int enabled = 0;
    for (Action<?> action : registry.all()) {
      if (action != null && action.isEnabled()) {
        enabled++;
      }
    }
    return enabled;
  }

  private record ActionTestStep(Action<?> action, ActionParams params) {
  }
}
