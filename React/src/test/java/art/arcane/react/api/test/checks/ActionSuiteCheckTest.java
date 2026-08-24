package art.arcane.react.api.test.checks;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.test.TestCheck;
import art.arcane.react.api.test.TestReport;
import art.arcane.react.api.test.TestStatus;
import art.arcane.react.content.action.ActionCollectGarbage;
import art.arcane.react.content.action.ActionPrewarmCriticalChunks;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.registry.Registry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class ActionSuiteCheckTest {
  @Test
  @SuppressWarnings("unchecked")
  void failedActionRecordsFailureAndAdvancesWithoutSuccessAnnouncement() throws ReflectiveOperationException {
    React previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Registry<IController> controllers = Mockito.mock(Registry.class);
    ActionController controller = Mockito.mock(ActionController.class);
    Registry<Action<?>> actions = Mockito.mock(Registry.class);
    Action<ActionParams> failedAction = Mockito.mock(Action.class);
    Action<ActionParams> successfulAction = Mockito.mock(Action.class);
    ActionParams failedParams = Mockito.mock(ActionParams.class);
    ActionParams successfulParams = Mockito.mock(ActionParams.class);
    IllegalStateException failure = new IllegalStateException("forced action failure");
    ActionTicket<ActionParams> failedTicket = Mockito.spy(new ActionTicket<>(failedAction, failedParams));
    ActionTicket<ActionParams> successfulTicket = Mockito.spy(new ActionTicket<>(successfulAction, successfulParams));
    AtomicInteger completionCount = new AtomicInteger(0);

    Mockito.when(controller.getActions()).thenReturn(actions);
    Mockito.when(controllers.get(ActionController.class)).thenReturn(controller);
    Mockito.when(actions.get(Mockito.anyString())).thenAnswer(invocation -> {
      String id = invocation.getArgument(0, String.class);
      if (ActionCollectGarbage.ID.equals(id)) {
        return failedAction;
      }
      if (ActionPrewarmCriticalChunks.ID.equals(id)) {
        return successfulAction;
      }
      return null;
    });
    configureAction(failedAction, ActionCollectGarbage.ID, failedParams, failedTicket);
    configureAction(successfulAction, ActionPrewarmCriticalChunks.ID, successfulParams, successfulTicket);
    Mockito.when(successfulAction.getCompletedMessage(Mockito.any())).thenReturn("successful action completed");
    Mockito.doAnswer(invocation -> {
      failedTicket.fail(failure);
      return null;
    }).when(failedTicket).queue();
    Mockito.doAnswer(invocation -> {
      successfulTicket.complete();
      return null;
    }).when(successfulTicket).queue();

    World world = Mockito.mock(World.class);
    Mockito.when(world.getSpawnLocation()).thenReturn(new Location(world, 0D, 64D, 0D));
    setField(plugin, "controllerRegistry", controllers);
    React.instance = plugin;
    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      TestReport report = new TestReport("Paper", "26.1", false, true, 0L);

      new ActionSuiteCheck().run(report, completionCount::incrementAndGet);

      TestCheck failed = find(report, ActionCollectGarbage.ID);
      TestCheck successful = find(report, ActionPrewarmCriticalChunks.ID);
      Assertions.assertEquals(TestStatus.FAIL, failed.status());
      Assertions.assertTrue(failed.detail().contains("forced action failure"));
      Assertions.assertEquals(TestStatus.PASS, successful.status());
      Assertions.assertEquals(1, completionCount.get());
      Mockito.verify(failedAction, Mockito.never()).getCompletedMessage(Mockito.any());
      Mockito.verify(successfulAction).getCompletedMessage(Mockito.any());
    } finally {
      React.instance = previous;
    }
  }

  private static void configureAction(
      Action<ActionParams> action,
      String id,
      ActionParams params,
      ActionTicket<ActionParams> ticket
  ) {
    Mockito.when(action.getId()).thenReturn(id);
    Mockito.when(action.isEnabled()).thenReturn(true);
    Mockito.when(action.getDefaultParams()).thenReturn(params);
    Mockito.doReturn(ticket).when(action).createForceful(Mockito.same(params));
  }

  private static TestCheck find(TestReport report, String name) {
    for (TestCheck check : report.checks()) {
      if (name.equals(check.name())) {
        return check;
      }
    }
    throw new AssertionError("Missing test result for " + name);
  }

  private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
    Field field = React.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
