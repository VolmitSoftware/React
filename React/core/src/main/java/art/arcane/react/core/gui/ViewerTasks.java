package art.arcane.react.core.gui;

import art.arcane.react.util.common.scheduling.J;

import java.util.UUID;

/**
 * Runs menu work on the thread that owns a viewer.
 */
@FunctionalInterface
interface ViewerTasks {
  ViewerTasks OWNER_THREAD = (viewerId, task, delayTicks) -> {
    Object handle = ReactSettingsMenu.handle(viewerId);
    if (handle != null) {
      J.runEntity(handle, task, delayTicks);
    }
  };

  void run(UUID viewerId, Runnable task, int delayTicks);
}
