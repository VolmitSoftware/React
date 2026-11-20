package art.arcane.react.api.web.profiler;

import art.arcane.react.platform.ProfilerHost;
import art.arcane.react.platform.ProfilerHost.FrameOwner;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Which plugin or mod owns each profiled frame, cached per class, or per method where the method name decides. */
public final class FrameOwners {
  public static final FrameOwner JAVA = new FrameOwner("java", "Java", true);
  public static final FrameOwner MINECRAFT = new FrameOwner("minecraft", "Minecraft", true);
  private static final List<String> JDK_PREFIXES = List.of("java.", "javax.", "jdk.", "sun.", "com.sun.", "org.graalvm.");

  private final Supplier<Optional<ProfilerHost>> host;
  private final ConcurrentHashMap<String, FrameOwner> classes = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, FrameOwner> methods = new ConcurrentHashMap<>();

  public FrameOwners(Supplier<Optional<ProfilerHost>> host) {
    this.host = host;
  }

  public FrameOwner ownerOf(String className, String methodName) {
    if (isJdk(className)) {
      return JAVA;
    }
    if (methodName.indexOf('$') >= 0) {
      return methods.computeIfAbsent(className + '#' + methodName, (String key) -> resolve(className, methodName));
    }
    return classes.computeIfAbsent(className, (String key) -> resolve(className, methodName));
  }

  public void clear() {
    classes.clear();
    methods.clear();
  }

  static boolean isJdk(String className) {
    for (String prefix : JDK_PREFIXES) {
      if (className.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  /** The loadable name behind a frame's class: lambdas and other hidden classes resolve to the class that made them. */
  static String lookupName(String className) {
    int lambda = className.indexOf("$$Lambda");
    String name = lambda > 0 ? className.substring(0, lambda) : className;
    int hidden = name.indexOf('/');
    return hidden > 0 ? name.substring(0, hidden) : name;
  }

  private FrameOwner resolve(String className, String methodName) {
    try {
      Optional<ProfilerHost> current = host.get();
      if (current.isEmpty()) {
        return MINECRAFT;
      }
      return current.get().ownerOf(lookupName(className), methodName).orElse(MINECRAFT);
    } catch (RuntimeException | LinkageError unresolved) {
      return MINECRAFT;
    }
  }
}
