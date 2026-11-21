package art.arcane.react.api.web.profiler;

import java.util.List;
import java.util.Set;

/**
 * Frames the profiler treats specially. An idle leaf is a thread waiting rather than working: parked, sleeping, in
 * {@code Object.wait}, blocked polling a selector or accepting a socket, or inside a foreign function call, which shows
 * only its method handle frame; its samples are left out by default.
 * Plumbing frames only hand work along (the JDK, executors, futures, mailboxes, DataFixer's Either and lambda glue);
 * runs of them collapse into one node of the call tree.
 */
public final class FrameKinds {
  private static final Set<String> IDLE_METHODS = Set.of(
      "jdk.internal.misc.Unsafe.park", "sun.misc.Unsafe.park",
      "java.lang.Object.wait", "java.lang.Object.wait0",
      "java.lang.Thread.sleep", "java.lang.Thread.sleep0", "java.lang.Thread.sleepNanos", "java.lang.Thread.sleepNanos0",
      "java.lang.Thread.yield", "java.lang.Thread.yield0", "java.lang.VirtualThread.takeVirtualThreadListToUnblock",
      "java.io.FileInputStream.readBytes", "java.io.FileInputStream.read0",
      "java.lang.ProcessHandleImpl.waitForProcessExit0",
      "io.netty.channel.epoll.Native.epollWait", "io.netty.channel.epoll.Native.epollWait0",
      "io.netty.channel.epoll.Native.epollBusyWait0", "io.netty.channel.kqueue.Native.keventWait",
      "io.netty.channel.kqueue.Native.keventWait0", "org.lwjgl.glfw.GLFW.glfwWaitEvents",
      "org.lwjgl.glfw.GLFW.glfwWaitEventsTimeout");
  private static final List<String> IDLE_CLASS_MARKERS = List.of("EPoll", "KQueue", "Poll", "Selector");
  private static final Set<String> SOCKET_WAITS = Set.of("accept", "accept0", "socketAccept", "poll", "poll0", "read0",
      "socketRead0", "wait", "select");
  private static final List<String> PLUMBING_PACKAGES = List.of("java.", "jdk.", "sun.");
  private static final List<String> PLUMBING_SIMPLE_PREFIXES = List.of("ForkJoin", "CompletableFuture", "Executor",
      "ProcessorMailbox", "BlockableEventLoop", "ReentrantBlockableEventLoop");
  private static final String NATIVE_DOWNCALL = "java.lang.invoke.LambdaForm$MH";
  private static final String EITHER = "com.mojang.datafixers.util.Either";

  private FrameKinds() {
  }

  public static boolean idle(String className, String methodName) {
    if (methodName.startsWith("park") && className.equals("java.util.concurrent.locks.LockSupport")) {
      return true;
    }
    if (IDLE_METHODS.contains(className + '.' + methodName) || className.startsWith(NATIVE_DOWNCALL)) {
      return true;
    }
    if (className.startsWith("sun.nio.ch.") || className.startsWith("java.net.")) {
      for (String marker : IDLE_CLASS_MARKERS) {
        if (className.contains(marker)) {
          return true;
        }
      }
      return SOCKET_WAITS.contains(methodName);
    }
    return false;
  }

  /** {@code className} is the readable class name. */
  public static boolean plumbing(String className, String methodName) {
    if (methodName.startsWith("lambda$") || className.contains("$$Lambda")) {
      return true;
    }
    for (String prefix : PLUMBING_PACKAGES) {
      if (className.startsWith(prefix)) {
        return true;
      }
    }
    if (className.startsWith(EITHER)) {
      return true;
    }
    String simple = className.substring(className.lastIndexOf('.') + 1);
    for (String prefix : PLUMBING_SIMPLE_PREFIXES) {
      if (simple.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
