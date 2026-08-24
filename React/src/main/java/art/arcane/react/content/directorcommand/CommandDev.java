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
import art.arcane.react.content.feature.FeatureCropFastForward;
import art.arcane.react.content.feature.FeatureLazyGravity;
import art.arcane.react.core.bridge.BridgeHealthReport;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.TweakController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.localization.catalog.DevMessages;
import art.arcane.react.model.AreaActionParams;
import art.arcane.react.nms.NmsBridge;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.react.util.project.registry.Registry;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Director(
    name = "dev",
    aliases = {"developer", "d"},
    origin = DirectorOrigin.BOTH,
    description = "Developer-only React validation and diagnostics",
    descriptionKey = "command.description.dev"
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
  private static final List<String> VERIFY_SAMPLER_IDS = List.of(
      "crop-fast-forward",
      "lazy-gravity-skipped",
      "hopper-chain-coalescing",
      "spawner-light-cache-skipped",
      "pdc-write-batcher",
      "explosion-packet-reduction",
      "per-world-tick-time",
      "world-save-duration"
  );

  @Director(
      name = "test-all",
      aliases = {"ta"},
      origin = DirectorOrigin.PLAYER,
      sync = true,
      description = "Audit React state, then queue the direct action suite one step at a time in your current world",
      descriptionKey = "command.description.dev.test_all"
  )
  public void testAll(
      @Param(
          name = "radius",
          description = "Chunk radius used for local purge entity and chunk test steps",
          descriptionKey = "command.parameter.dev.radius",
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
      ReactLanguage.sendPrefixed(commandSender, CommandMessages.ACTION_CONTROLLER_UNAVAILABLE);
      return;
    }

    sendRuntimeAudit(commandSender, actionController);

    List<ActionTestStep> steps = buildTestSteps(actionController, world, executingPlayer, safeRadius);
    if (steps.isEmpty()) {
      ReactLanguage.sendPrefixed(commandSender, DevMessages.ACTIONS_NONE);
      return;
    }

    List<String> uncoveredActions = findUncoveredEnabledActions(actionController);
    ReactLanguage.sendPrefixed(
        commandSender,
        DevMessages.SUITE_SCOPE,
        MessageArgument.untrusted("world", world.getName()),
        MessageArgument.untrusted("radius", safeRadius)
    );
    ReactLanguage.sendPrefixed(
        commandSender,
        DevMessages.SUITE_QUEUEING,
        MessageArgument.untrusted("count", steps.size())
    );
    if (!uncoveredActions.isEmpty()) {
      ReactLanguage.sendPrefixed(
          commandSender,
          DevMessages.SUITE_UNCOVERED,
          MessageArgument.untrusted("actions", String.join(", ", uncoveredActions))
      );
    }

    queueStep(commandSender, steps, 0);
  }

  @Director(
      name = "verify",
      aliases = {"v", "selftest"},
      origin = DirectorOrigin.BOTH,
      sync = true,
      description = "Verify bridge, sampler, and lazy-gravity behavior and report PASS or FAIL",
      descriptionKey = "command.description.dev.verify"
  )
  public void verify() {
    VolmitSender out = sender();
    ReactLanguage.sendPrefixed(out, DevMessages.VERIFY_HEADER);
    ReactLanguage.send(out, J.isFoliaThreading() ? DevMessages.VERIFY_PLATFORM_FOLIA : DevMessages.VERIFY_PLATFORM_SINGLE);

    verifyBridge(out);
    verifySamplers(out);
    verifyCropObservational(out);
    verifyLazyGravity(out, () -> ReactLanguage.sendPrefixed(out, DevMessages.VERIFY_COMPLETE));
  }

  private void verifyBridge(VolmitSender out) {
    NmsBridge bridge = NmsBridges.get();
    if (bridge == null) {
      ReactLanguage.send(
          out,
          DevMessages.BRIDGE_SKIPPED,
          MessageArgument.untrusted("reason", NmsBridges.failureReason())
      );
      return;
    }

    BridgeHealthReport report = React.bridgeRegistry().snapshotHealth();
    boolean healthy = report.unavailableCount() == 0;
    ReactLanguage.send(
        out,
        healthy ? DevMessages.BRIDGE_PASS : DevMessages.BRIDGE_FAIL,
        MessageArgument.untrusted("available", report.availableCount()),
        MessageArgument.untrusted("unavailable", report.unavailableCount())
    );
    if (!healthy) {
      for (BridgeHealthReport.BridgeHealthEntry entry : report.entries()) {
        if (!entry.available()) {
          ReactLanguage.send(
              out,
              DevMessages.BRIDGE_FAILURE,
              MessageArgument.untrusted("id", entry.logicalId()),
              MessageArgument.untrusted("reason", entry.failureReason())
          );
        }
      }
    }

    FeatureLazyGravity gravity = React.feature(FeatureLazyGravity.class);
    if (gravity != null) {
      boolean hookActive = !gravity.isMeasurementOnly();
      ReactLanguage.send(out, hookActive ? DevMessages.HOOKS_PASS : DevMessages.HOOKS_WARN);
    }
    ReactLanguage.send(out, DevMessages.HOOKS_RESET);
  }

  private void verifySamplers(VolmitSender out) {
    int missing = 0;
    int invalid = 0;
    List<String> values = new ArrayList<>();
    for (String id : VERIFY_SAMPLER_IDS) {
      Sampler sampler = React.sampler(id);
      if (sampler == null) {
        missing++;
        ReactLanguage.send(out, DevMessages.SAMPLER_MISSING, MessageArgument.untrusted("id", id));
        continue;
      }

      double value = sampler.sample();
      if (Double.isNaN(value) || Double.isInfinite(value) || value < 0D) {
        invalid++;
        ReactLanguage.send(
            out,
            DevMessages.SAMPLER_INVALID,
            MessageArgument.untrusted("id", id),
            MessageArgument.untrusted("value", value)
        );
        continue;
      }

      values.add(id + "=" + sampler.format(value));
    }

    boolean pass = missing == 0 && invalid == 0;
    ReactLanguage.send(
        out,
        pass ? DevMessages.SAMPLERS_PASS : DevMessages.SAMPLERS_FAIL,
        MessageArgument.untrusted("checked", VERIFY_SAMPLER_IDS.size()),
        MessageArgument.untrusted("missing", missing),
        MessageArgument.untrusted("invalid", invalid)
    );
    if (!values.isEmpty()) {
      ReactLanguage.send(
          out,
          DevMessages.SAMPLER_VALUES,
          MessageArgument.untrusted("values", String.join(", ", values))
      );
    }
  }

  private void verifyCropObservational(VolmitSender out) {
    FeatureCropFastForward crop = React.feature(FeatureCropFastForward.class);
    if (crop == null) {
      ReactLanguage.send(out, DevMessages.CROP_SKIPPED);
      return;
    }

    Sampler sampler = React.sampler("crop-fast-forward");
    String rate = sampler == null ? "n/a" : sampler.format(sampler.sample());
    ReactLanguage.send(
        out,
        DevMessages.CROP_INFO,
        MessageArgument.untrusted("enabled", crop.isEnabled()),
        MessageArgument.untrusted("rate", rate)
    );
    ReactLanguage.send(out, DevMessages.CROP_OBSERVE);
  }

  private void verifyLazyGravity(VolmitSender out, Runnable onDone) {
    FeatureLazyGravity gravity = React.feature(FeatureLazyGravity.class);
    if (gravity == null) {
      ReactLanguage.send(out, DevMessages.GRAVITY_NOT_REGISTERED);
      onDone.run();
      return;
    }
    if (Bukkit.getWorlds().isEmpty()) {
      ReactLanguage.send(out, DevMessages.GRAVITY_NO_WORLDS);
      onDone.run();
      return;
    }

    World world = Bukkit.getWorlds().get(0);
    Location base = world.getSpawnLocation();
    int x = base.getBlockX();
    int z = base.getBlockZ();
    int groundY = world.getHighestBlockYAt(x, z);
    int spawnY = Math.min(world.getMaxHeight() - 2, groundY + 20);
    if (spawnY <= groundY + 2) {
      ReactLanguage.send(
          out,
          DevMessages.GRAVITY_NO_HEADROOM,
          MessageArgument.untrusted("ground_y", groundY)
      );
      onDone.run();
      return;
    }

    Location dropAt = new Location(world, x + 0.5D, spawnY, z + 0.5D);
    FallingBlock falling = world.spawnFallingBlock(dropAt, Material.SAND.createBlockData());
    UUID id = falling.getUniqueId();
    ReactLanguage.send(
        out,
        DevMessages.GRAVITY_DROPPED,
        MessageArgument.untrusted("spawn_y", spawnY),
        MessageArgument.untrusted("ground_y", groundY)
    );

    J.s(() -> {
      Entity entity = Bukkit.getEntity(id);
      boolean landed = entity == null || entity.isDead() || !entity.isValid();
      ReactLanguage.send(out, landed ? DevMessages.GRAVITY_PASS : DevMessages.GRAVITY_FAIL);
      if (!landed && entity != null) {
        entity.remove();
      }
      Block landingBlock = world.getBlockAt(x, groundY + 1, z);
      if (landingBlock.getType() == Material.SAND) {
        landingBlock.setType(Material.AIR);
      }
      onDone.run();
    }, 100);
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

    ReactLanguage.sendPrefixed(commandSender, DevMessages.AUDIT_HEADER);
    ReactLanguage.sendPrefixed(
        commandSender,
        DevMessages.AUDIT_FEATURES,
        MessageArgument.untrusted("registered", registeredFeatures),
        MessageArgument.untrusted("enabled", enabledFeatures),
        MessageArgument.untrusted("active", activeFeatures)
    );
    ReactLanguage.sendPrefixed(
        commandSender,
        DevMessages.AUDIT_TWEAKS,
        MessageArgument.untrusted("registered", registeredTweaks),
        MessageArgument.untrusted("enabled", enabledTweaks),
        MessageArgument.untrusted("active", activeTweaks)
    );
    ReactLanguage.sendPrefixed(
        commandSender,
        DevMessages.AUDIT_SAMPLERS,
        MessageArgument.untrusted("registered", registeredSamplers)
    );
    ReactLanguage.sendPrefixed(
        commandSender,
        DevMessages.AUDIT_ACTIONS,
        MessageArgument.untrusted("registered", registeredActions),
        MessageArgument.untrusted("enabled", enabledActions)
    );
    ReactLanguage.sendPrefixed(
        commandSender,
        DevMessages.AUDIT_EXCLUDED,
        MessageArgument.untrusted("incident", ActionIncidentPlaybook.ID),
        MessageArgument.untrusted("unknown", ActionUnknown.ID)
    );
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

      area.selectLoadedRadius(
          world,
          executingPlayer.getLocation().getChunk().getX(),
          executingPlayer.getLocation().getChunk().getZ(),
          radius
      );
      return purgeEntitiesParams;
    }

    if (ActionPurgeChunks.ID.equals(actionId) && params instanceof ActionPurgeChunks.Params purgeChunksParams) {
      AreaActionParams area = purgeChunksParams.getArea();
      if (area == null) {
        area = AreaActionParams.builder().build();
        purgeChunksParams.setArea(area);
      }

      area.selectLoadedRadius(
          world,
          executingPlayer.getLocation().getChunk().getX(),
          executingPlayer.getLocation().getChunk().getZ(),
          radius
      );
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

  private void queueStep(VolmitSender commandSender, List<ActionTestStep> steps, int index) {
    if (index >= steps.size()) {
      ReactLanguage.sendPrefixed(commandSender, DevMessages.SUITE_FINISHED);
      return;
    }

    ActionTestStep step = steps.get(index);
    Action<?> action = step.action();
    if (action == null || !action.isEnabled()) {
      ReactLanguage.sendPrefixed(
          commandSender,
          DevMessages.SUITE_SKIPPED,
          MessageArgument.untrusted("index", index + 1),
          MessageArgument.untrusted("total", steps.size()),
          MessageArgument.untrusted("action", action == null ? "unknown-action" : action.getId())
      );
      queueStep(commandSender, steps, index + 1);
      return;
    }

    ActionTicket<?> ticket;
    try {
      ticket = action.createForceful(step.params());
    } catch (Throwable e) {
      String detail = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage());
      ReactLanguage.sendPrefixed(
          commandSender,
          DevMessages.SUITE_CREATE_FAILED,
          MessageArgument.untrusted("index", index + 1),
          MessageArgument.untrusted("total", steps.size()),
          MessageArgument.untrusted("action", action.getId()),
          MessageArgument.untrusted("detail", detail)
      );
      React.reportError(e);
      queueStep(commandSender, steps, index + 1);
      return;
    }

    Action<?> queuedAction = action;
    ticket.onStart((started) -> ReactLanguage.sendPrefixed(
            commandSender,
            DevMessages.SUITE_STARTING,
            MessageArgument.untrusted("index", index + 1),
            MessageArgument.untrusted("total", steps.size()),
            MessageArgument.untrusted("action", queuedAction.getId())
        ))
        .onTerminal((completed) -> {
          if (completed.isFailed()) {
            Throwable failure = completed.getFailure();
            String detail = failure == null
                ? "unknown failure"
                : failure.getClass().getSimpleName()
                + (failure.getMessage() == null ? "" : " - " + failure.getMessage());
            ReactLanguage.sendPrefixed(
                commandSender,
                DevMessages.SUITE_FAILED,
                MessageArgument.untrusted("index", index + 1),
                MessageArgument.untrusted("total", steps.size()),
                MessageArgument.untrusted("action", queuedAction.getId()),
                MessageArgument.untrusted("detail", detail)
            );
          } else {
            ReactLanguage.sendPrefixed(
                commandSender,
                DevMessages.SUITE_COMPLETED,
                MessageArgument.untrusted("index", index + 1),
                MessageArgument.untrusted("total", steps.size()),
                MessageArgument.untrusted("message", completedMessage(queuedAction, completed))
            );
          }
          queueStep(commandSender, steps, index + 1);
        });

    ReactLanguage.sendPrefixed(
        commandSender,
        DevMessages.SUITE_QUEUED,
        MessageArgument.untrusted("index", index + 1),
        MessageArgument.untrusted("total", steps.size()),
        MessageArgument.untrusted("action", action.getId())
    );
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
