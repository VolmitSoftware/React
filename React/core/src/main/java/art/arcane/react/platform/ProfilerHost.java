package art.arcane.react.platform;

import java.util.Optional;

/**
 * What the sampling profiler needs from the platform: the thread the game ticks on and which plugin or mod owns the
 * class named in a stack frame. Called from the profiler and web threads, never from the server thread.
 */
public interface ProfilerHost {
  /** The thread the game ticks on; the integrated server thread in singleplayer. */
  Optional<Thread> serverThread();

  /**
   * The owner of a frame. {@code methodName} matters where injected code keeps its author in the method name, such as
   * a mixin handler. Empty when the class belongs to no plugin or mod.
   */
  Optional<FrameOwner> ownerOf(String className, String methodName);

  /**
   * Loads the runtime-to-Mojang name map where the game runs obfuscated names; called once as the profiler starts, on
   * its own thread. Never throws: without a map, frames keep their runtime names.
   */
  default void loadNames() {
  }

  /** The Mojang name of a runtime class name, or the name itself. */
  default String readableClass(String className) {
    return className;
  }

  /** The Mojang name of a runtime method name, or the name itself. */
  default String readableMethod(String methodName) {
    return methodName;
  }

  /**
   * One owner of profiled frames. {@code platform} owners (the JDK, the server and the loader) are skipped when a
   * sample is charged to the innermost plugin or mod frame.
   */
  record FrameOwner(String id, String name, boolean platform) {
  }
}
