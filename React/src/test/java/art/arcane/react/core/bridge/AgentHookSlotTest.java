package art.arcane.react.core.bridge;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class AgentHookSlotTest {
  @Test
  void aHookRequestedBeforeDeactivationIsDroppedWhenTheAgentArrivesLate() {
    List<Runnable> waiting = new ArrayList<>();
    AgentHookSlot slot = new AgentHookSlot(waiting::add, () -> false);
    AtomicInteger installed = new AtomicInteger();
    AtomicInteger uninstalled = new AtomicInteger();

    slot.install(installed::incrementAndGet);
    slot.release(uninstalled::incrementAndGet);
    waiting.forEach(Runnable::run);

    Assertions.assertEquals(0, installed.get());
    Assertions.assertEquals(0, uninstalled.get());
  }

  @Test
  void onlyTheLatestActivationInstallsItsHook() {
    List<Runnable> waiting = new ArrayList<>();
    AgentHookSlot slot = new AgentHookSlot(waiting::add, () -> false);
    List<String> installed = new ArrayList<>();

    slot.install(() -> installed.add("first"));
    slot.install(() -> installed.add("second"));
    waiting.forEach(Runnable::run);

    Assertions.assertEquals(List.of("second"), installed);
  }

  @Test
  void anAttachedAgentInstallsAndUninstallsWithoutWaiting() {
    AgentHookSlot slot = new AgentHookSlot(Runnable::run, () -> true);
    AtomicInteger installed = new AtomicInteger();
    AtomicInteger uninstalled = new AtomicInteger();

    slot.install(installed::incrementAndGet);
    Assertions.assertEquals(1, installed.get());

    slot.release(uninstalled::incrementAndGet);
    Assertions.assertEquals(1, uninstalled.get());
  }

  @Test
  void anUnavailableAgentNeverReachesTheBridge() {
    AgentHookSlot slot = new AgentHookSlot(dropped -> { }, () -> false);
    AtomicInteger bridgeCalls = new AtomicInteger();

    for (int toggle = 0; toggle < 3; toggle++) {
      slot.install(bridgeCalls::incrementAndGet);
      slot.release(bridgeCalls::incrementAndGet);
    }

    Assertions.assertEquals(0, bridgeCalls.get());
  }
}
