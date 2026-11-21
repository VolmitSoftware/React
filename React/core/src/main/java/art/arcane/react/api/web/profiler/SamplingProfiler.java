package art.arcane.react.api.web.profiler;

import art.arcane.react.platform.ProfilerHost;
import art.arcane.react.platform.ProfilerHost.FrameOwner;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * React's sampling profiler. A daemon thread reads the server thread's stack, or every running thread's, each
 * {@code intervalMs} and merges it into a {@link CallTree}. Off until started; stops itself after {@code maxSeconds}.
 */
public final class SamplingProfiler {
  public static final int DEFAULT_INTERVAL_MS = 10;
  public static final int MIN_INTERVAL_MS = 1;
  public static final int MAX_INTERVAL_MS = 1000;
  public static final int DEFAULT_MAX_SECONDS = 600;
  public static final int MAX_SECONDS = 3600;
  public static final String SERVER = "server";
  public static final String ALL = "all";
  private static final String SERVER_THREAD_NAME = "Server thread";

  private final Supplier<Optional<ProfilerHost>> host;
  private final LongSupplier clock;
  private final FrameOwners owners;
  private volatile CallTree tree;
  private volatile Thread sampler;
  private volatile int intervalMs = DEFAULT_INTERVAL_MS;
  private volatile String threads = SERVER;
  private volatile long startedAt;
  private volatile long stopsAt;
  private volatile long stoppedAt;

  public SamplingProfiler(Supplier<Optional<ProfilerHost>> host) {
    this(host, System::currentTimeMillis);
  }

  public SamplingProfiler(Supplier<Optional<ProfilerHost>> host, LongSupplier clock) {
    this.host = host;
    this.clock = clock;
    this.owners = new FrameOwners(host);
    this.tree = newTree();
  }

  /**
   * Starts sampling, or retunes a running profiler in place. A fresh start clears the last profile. {@code server} falls
   * back to {@code all} where the platform has no server thread.
   */
  public synchronized ProfileDtos.State start(int intervalMs, String threads, int maxSeconds) {
    this.intervalMs = clampInterval(intervalMs);
    String mode = ALL.equalsIgnoreCase(threads) ? ALL : SERVER;
    if (mode.equals(SERVER) && serverThread() == null) {
      mode = ALL;
    }
    long now = clock.getAsLong();
    if (sampler == null) {
      tree = newTree();
      startedAt = now;
      stoppedAt = 0;
      this.threads = mode;
      stopsAt = now + clampSeconds(maxSeconds) * 1000L;
      Thread thread = new Thread(this::run, "react-profiler");
      thread.setDaemon(true);
      sampler = thread;
      thread.start();
    } else {
      if (!mode.equals(this.threads)) {
        tree = newTree();
        startedAt = now;
        this.threads = mode;
      }
      stopsAt = Math.max(now, startedAt + clampSeconds(maxSeconds) * 1000L);
    }
    return state();
  }

  public synchronized ProfileDtos.State stop() {
    Thread thread = sampler;
    sampler = null;
    if (thread != null) {
      stoppedAt = clock.getAsLong();
      LockSupport.unpark(thread);
    }
    return state();
  }

  /** Clears the profile; a running profiler keeps sampling into the empty tree. */
  public synchronized ProfileDtos.State reset() {
    tree = newTree();
    long now = clock.getAsLong();
    if (sampler != null) {
      startedAt = now;
    } else {
      startedAt = 0;
      stoppedAt = 0;
      stopsAt = 0;
    }
    return state();
  }

  public boolean running() {
    return sampler != null;
  }

  public ProfileDtos.State state() {
    boolean running = sampler != null;
    long started = startedAt;
    long end = running ? clock.getAsLong() : stoppedAt;
    long duration = started == 0 ? 0 : Math.max(0, end - started);
    return new ProfileDtos.State(running, intervalMs, threads, started == 0 ? null : started,
        running ? Long.valueOf(stopsAt) : null, tree.samples(), duration);
  }

  public ProfileDtos.Owners owners(boolean idle) {
    CallTree current = warm();
    return current.owners(this::ownerOf, idle);
  }

  public ProfileDtos.Hot hot(String ownerId, int limit, boolean idle) {
    CallTree current = warm();
    return current.hot(this::ownerOf, blankToNull(ownerId), limit, idle);
  }

  public ProfileDtos.Tree tree(CallTree.Query query) {
    CallTree current = warm();
    return current.tree(this::ownerOf, new CallTree.Query(blankToNull(query.ownerId()), blankToNull(query.thread()),
        query.minPercent(), query.maxDepth(), query.group(), query.collapse(), query.idle()));
  }

  public ProfileDtos.Threads threads() {
    return tree.threads();
  }

  /** Everything at once, for a snapshot. */
  public ProfileDtos.Profile profile() {
    CallTree current = warm();
    return new ProfileDtos.Profile(state(), current.owners(this::ownerOf, false), current.hot(this::ownerOf, null, 500, false),
        current.threads(), current.tree(this::ownerOf, new CallTree.Query(null, null, 0.01D, CallTree.MAX_DEPTH, true, true, false)));
  }

  public static int clampInterval(int intervalMs) {
    return Math.max(MIN_INTERVAL_MS, Math.min(MAX_INTERVAL_MS, intervalMs));
  }

  public static int clampSeconds(int seconds) {
    return Math.max(1, Math.min(MAX_SECONDS, seconds));
  }

  /** A tree that names frames through the platform's mappings, loaded the first time a profile starts. */
  private CallTree newTree() {
    return new CallTree(CallTree.DEFAULT_MAX_NODES, false, new FrameNames(this::readableClass, this::readableMethod));
  }

  private String readableClass(String className) {
    Optional<ProfilerHost> current = host.get();
    return current.isPresent() ? current.get().readableClass(className) : className;
  }

  private String readableMethod(String methodName) {
    Optional<ProfilerHost> current = host.get();
    return current.isPresent() ? current.get().readableMethod(methodName) : methodName;
  }

  private FrameOwner ownerOf(String className, String methodName) {
    return owners.ownerOf(className, methodName);
  }

  /** Resolves owners outside the tree lock, so a slow first lookup never holds up the sampler. */
  private CallTree warm() {
    CallTree current = tree;
    for (String className : current.classNames()) {
      owners.ownerOf(className, "");
    }
    return current;
  }

  private Thread serverThread() {
    Optional<ProfilerHost> current = host.get();
    Optional<Thread> thread = current.isPresent() ? current.get().serverThread() : Optional.empty();
    if (thread.isPresent() && thread.get().isAlive()) {
      return thread.get();
    }
    for (Thread candidate : Thread.getAllStackTraces().keySet()) {
      if (SERVER_THREAD_NAME.equals(candidate.getName()) && candidate.isAlive()) {
        return candidate;
      }
    }
    return null;
  }

  private void run() {
    Thread self = Thread.currentThread();
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    Thread target = null;
    host.get().ifPresent(ProfilerHost::loadNames);
    long next = System.nanoTime();
    while (sampler == self) {
      if (clock.getAsLong() >= stopsAt) {
        synchronized (this) {
          if (sampler == self) {
            sampler = null;
            stoppedAt = clock.getAsLong();
          }
        }
        return;
      }
      CallTree current = tree;
      try {
        if (SERVER.equals(threads)) {
          if (target == null || !target.isAlive()) {
            target = serverThread();
          }
          if (target != null) {
            StackTraceElement[] stack = target.getStackTrace();
            current.add(target.getName(), target.getState(), stack);
          }
        } else {
          sampleAll(bean, self.getId(), current);
        }
      } catch (RuntimeException | LinkageError failure) {
        target = null;
      }
      next += intervalMs * 1_000_000L;
      long wait = next - System.nanoTime();
      if (wait <= 0) {
        next = System.nanoTime();
      } else {
        LockSupport.parkNanos(this, wait);
      }
    }
  }

  private static void sampleAll(ThreadMXBean bean, long selfId, CallTree current) {
    for (ThreadInfo info : bean.dumpAllThreads(false, false, CallTree.MAX_DEPTH)) {
      if (info == null || info.getThreadId() == selfId) {
        continue;
      }
      Thread.State state = info.getThreadState();
      StackTraceElement[] stack = info.getStackTrace();
      if (state != Thread.State.RUNNABLE && state != Thread.State.BLOCKED || stack.length == 0 || idle(stack[0])) {
        continue;
      }
      current.add(info.getThreadName(), state, stack);
    }
  }

  /**
   * Whether a thread that reports RUNNABLE is really waiting in native code: polling a selector, accepting or reading
   * a socket, or reading the console. Such threads are left out of {@code all}, as sleeping ones are.
   */
  static boolean idle(StackTraceElement leaf) {
    String className = leaf.getClassName();
    String method = leaf.getMethodName();
    if (!leaf.isNativeMethod()) {
      return false;
    }
    if (className.startsWith("sun.nio.ch.") || className.startsWith("java.net.") || className.startsWith("sun.nio.fs.")
        || className.startsWith("io.netty.channel.") || className.equals("java.io.FileInputStream")
        || className.equals("java.lang.ProcessHandleImpl")) {
      return true;
    }
    return method.startsWith("park") || method.startsWith("sleep") || method.startsWith("wait") || method.equals("yield0");
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
