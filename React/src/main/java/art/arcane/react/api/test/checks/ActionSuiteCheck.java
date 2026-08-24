package art.arcane.react.api.test.checks;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.test.ReactAsyncSubsystemCheck;
import art.arcane.react.api.test.TestReport;
import art.arcane.react.content.action.ActionCollectGarbage;
import art.arcane.react.content.action.ActionHopperNetworkNormalize;
import art.arcane.react.content.action.ActionPrewarmCriticalChunks;
import art.arcane.react.content.action.ActionPurgeChunks;
import art.arcane.react.content.action.ActionPurgeEntities;
import art.arcane.react.content.action.ActionQuarantineHotChunks;
import art.arcane.react.content.action.ActionTrimEntitiesByAgePriority;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.TestMessages;
import art.arcane.react.model.AreaActionParams;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.react.util.project.registry.Registry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ActionSuiteCheck implements ReactAsyncSubsystemCheck {
  private static final String SUBSYSTEM = "actions";
  private static final int PURGE_RADIUS = 2;
  private static final List<String> TEST_ACTION_ORDER = List.of(
      ActionCollectGarbage.ID,
      ActionPrewarmCriticalChunks.ID,
      ActionQuarantineHotChunks.ID,
      ActionHopperNetworkNormalize.ID,
      ActionTrimEntitiesByAgePriority.ID,
      ActionPurgeEntities.ID,
      ActionPurgeChunks.ID
  );

  @Override
  public String subsystem() {
    return SUBSYSTEM;
  }

  @Override
  public void run(TestReport report, Runnable onDone) {
    ActionController controller = React.controller(ActionController.class);
    if (controller == null || controller.getActions() == null) {
      report.skip(SUBSYSTEM, "action-suite", ReactLanguage.raw(TestMessages.ACTION_CONTROLLER_UNAVAILABLE));
      onDone.run();
      return;
    }

    if (Bukkit.getWorlds().isEmpty()) {
      for (String actionId : TEST_ACTION_ORDER) {
        report.skip(SUBSYSTEM, actionId, ReactLanguage.raw(TestMessages.ACTION_NO_WORLDS));
      }
      onDone.run();
      return;
    }

    World world = Bukkit.getWorlds().get(0);
    Location spawn = world.getSpawnLocation();
    int centerChunkX = spawn.getBlockX() >> 4;
    int centerChunkZ = spawn.getBlockZ() >> 4;

    Registry<Action<?>> registry = controller.getActions();
    List<Step> steps = new ArrayList<Step>();
    for (String actionId : TEST_ACTION_ORDER) {
      Action<?> action = registry.get(actionId);
      if (action == null) {
        report.skip(SUBSYSTEM, actionId, ReactLanguage.raw(TestMessages.ACTION_NOT_REGISTERED));
        continue;
      }

      if (!action.isEnabled()) {
        report.skip(SUBSYSTEM, actionId, ReactLanguage.raw(TestMessages.ACTION_DISABLED));
        continue;
      }

      try {
        ActionParams params = prepareParams(actionId, action.getDefaultParams(), world, centerChunkX, centerChunkZ);
        steps.add(new Step(actionId, action, params));
      } catch (Throwable e) {
        report.fail(
            SUBSYSTEM,
            actionId,
            ReactLanguage.raw(
                TestMessages.ACTION_PREPARE_FAILED,
                MessageArgument.untrusted("reason", describe(e))
            )
        );
        React.reportError(e);
      }
    }

    if (steps.isEmpty()) {
      onDone.run();
      return;
    }

    runStep(report, steps, 0, onDone);
  }

  private void runStep(TestReport report, List<Step> steps, int index, Runnable onDone) {
    if (index >= steps.size()) {
      onDone.run();
      return;
    }

    Step step = steps.get(index);
    String stepId = step.actionId();
    Action<?> stepAction = step.action();

    ActionTicket<?> ticket;
    try {
      ticket = stepAction.createForceful(step.params());
    } catch (Throwable e) {
      report.fail(
          SUBSYSTEM,
          stepId,
          ReactLanguage.raw(
              TestMessages.ACTION_CREATE_FAILED,
              MessageArgument.untrusted("reason", describe(e))
          )
      );
      React.reportError(e);
      runStep(report, steps, index + 1, onDone);
      return;
    }

    AtomicBoolean advanced = new AtomicBoolean(false);
    ticket.onTerminal((completed) -> {
      if (!advanced.compareAndSet(false, true)) {
        return;
      }

      try {
        if (completed.isFailed()) {
          report.fail(
              SUBSYSTEM,
              stepId,
              ReactLanguage.raw(
                  TestMessages.ACTION_FAILED,
                  MessageArgument.untrusted("reason", describe(completed.getFailure()))
              )
          );
        } else {
          report.pass(SUBSYSTEM, stepId, completedMessage(stepAction, completed));
        }
      } catch (Throwable e) {
        report.fail(
            SUBSYSTEM,
            stepId,
            ReactLanguage.raw(
                TestMessages.ACTION_COMPLETION_FAILED,
                MessageArgument.untrusted("reason", describe(e))
            )
        );
        React.reportError(e);
      }

      runStep(report, steps, index + 1, onDone);
    });

    ticket.queue();
  }

  private ActionParams prepareParams(String actionId, ActionParams params, World world, int centerChunkX, int centerChunkZ) {
    if (ActionPurgeEntities.ID.equals(actionId) && params instanceof ActionPurgeEntities.Params purgeEntitiesParams) {
      AreaActionParams area = purgeEntitiesParams.getArea();
      if (area == null) {
        area = AreaActionParams.builder().build();
        purgeEntitiesParams.setArea(area);
      }

      area.selectLoadedRadius(world, centerChunkX, centerChunkZ, PURGE_RADIUS);
      return purgeEntitiesParams;
    }

    if (ActionPurgeChunks.ID.equals(actionId) && params instanceof ActionPurgeChunks.Params purgeChunksParams) {
      AreaActionParams area = purgeChunksParams.getArea();
      if (area == null) {
        area = AreaActionParams.builder().build();
        purgeChunksParams.setArea(area);
      }

      area.selectLoadedRadius(world, centerChunkX, centerChunkZ, PURGE_RADIUS);
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

  @SuppressWarnings({"rawtypes", "unchecked"})
  private String completedMessage(Action<?> action, ActionTicket<?> ticket) {
    return ((Action) action).getCompletedMessage((ActionTicket) ticket);
  }

  private String describe(Throwable e) {
    return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage());
  }

  private record Step(String actionId, Action<?> action, ActionParams params) {
  }
}
