package art.arcane.react.api.web.profiler;

import art.arcane.react.platform.ProfilerHost.FrameOwner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Sampled stacks merged into one call tree per thread. Each node counts the samples that passed through it (total) and
 * the samples that ended in it (self). Past {@code maxNodes} a sample stops at the deepest node that already exists, so
 * memory stays bounded and only the deepest detail is lost. Every method is synchronized: the sampler adds while web
 * threads read.
 */
public final class CallTree {
  public static final int DEFAULT_MAX_NODES = 200_000;
  public static final int MAX_DEPTH = 256;
  public static final String OTHER = "(other)";
  public static final String THREAD_OWNER = "thread";
  private static final int INDEX_AT = 16;
  private static final Node[] NO_CHILDREN = new Node[0];

  private final int maxNodes;
  private final boolean lines;
  private final Map<String, Node> threads = new LinkedHashMap<>();
  private final Map<String, String> threadStates = new HashMap<>();
  private final Set<String> classNames = new HashSet<>();
  private int nodes;
  private long samples;

  public CallTree(int maxNodes, boolean lines) {
    this.maxNodes = Math.max(1, maxNodes);
    this.lines = lines;
  }

  /** One sample of one thread; {@code stack[0]} is the innermost frame, as {@link Thread#getStackTrace()} returns it. */
  public synchronized void add(String thread, Thread.State state, StackTraceElement[] stack) {
    Node node = threads.get(thread);
    if (node == null) {
      if (nodes >= maxNodes) {
        return;
      }
      node = new Node(null, thread, -1, thread);
      threads.put(thread, node);
      nodes++;
    }
    threadStates.put(thread, state == null ? "" : state.name());
    samples++;
    node.total++;
    int last = Math.max(0, stack.length - MAX_DEPTH);
    for (int index = stack.length - 1; index >= last; index--) {
      StackTraceElement frame = stack[index];
      String className = frame.getClassName();
      String methodName = frame.getMethodName();
      int line = lines ? frame.getLineNumber() : -1;
      Node child = node.child(className, methodName, line);
      if (child == null) {
        if (nodes >= maxNodes) {
          break;
        }
        child = node.addChild(new Node(className, methodName, line, display(className, methodName, line)));
        classNames.add(className);
        nodes++;
      }
      child.total++;
      node = child;
    }
    node.self++;
  }

  public synchronized void clear() {
    threads.clear();
    threadStates.clear();
    classNames.clear();
    nodes = 0;
    samples = 0;
  }

  public synchronized long samples() {
    return samples;
  }

  public synchronized int nodes() {
    return nodes;
  }

  /** Every class seen so far, to resolve owners before a query takes the lock. */
  public synchronized List<String> classNames() {
    return new ArrayList<>(classNames);
  }

  /** Samples charged to the innermost plugin or mod frame of each stack, with each owner's three busiest methods. */
  public synchronized ProfileDtos.Owners owners(BiFunction<String, String, FrameOwner> owners) {
    Map<String, OwnerTally> tallies = new HashMap<>();
    for (Node root : threads.values()) {
      rank(root, owners, null, null, null, tallies);
    }
    List<ProfileDtos.OwnerRank> ranked = new ArrayList<>(tallies.size());
    for (OwnerTally tally : tallies.values()) {
      List<ProfileDtos.MethodCount> top = tally.methods.entrySet().stream()
          .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
          .limit(3)
          .map((Map.Entry<String, Long> entry) -> new ProfileDtos.MethodCount(entry.getKey(), entry.getValue()))
          .toList();
      ranked.add(new ProfileDtos.OwnerRank(tally.owner.id(), tally.owner.name(), tally.samples, percent(tally.samples, samples), top));
    }
    ranked.sort(Comparator.comparingLong(ProfileDtos.OwnerRank::samples).reversed().thenComparing(ProfileDtos.OwnerRank::id));
    return new ProfileDtos.Owners(samples, ranked);
  }

  private void rank(Node node, BiFunction<String, String, FrameOwner> owners, FrameOwner charged, String chargedMethod,
                    String serverMethod, Map<String, OwnerTally> tallies) {
    FrameOwner owner = charged;
    String method = chargedMethod;
    String server = serverMethod;
    if (node.className != null) {
      FrameOwner frameOwner = owners.apply(node.className, node.methodName);
      if (!frameOwner.platform()) {
        owner = frameOwner;
        method = node.method;
      } else if (!FrameOwners.JAVA.id().equals(frameOwner.id())) {
        server = node.method;
      }
    }
    if (node.self > 0) {
      FrameOwner target = owner == null ? FrameOwners.MINECRAFT : owner;
      String top = owner == null ? server : method;
      OwnerTally tally = tallies.computeIfAbsent(target.id(), (String id) -> new OwnerTally(target));
      tally.samples += node.self;
      if (top != null) {
        tally.methods.merge(top, node.self, Long::sum);
      }
    }
    for (int index = 0; index < node.count; index++) {
      rank(node.children[index], owners, owner, method, server, tallies);
    }
  }

  /**
   * Methods by self samples, then total. A method's total counts each sample once, however often it recurses. With an
   * {@code ownerId}, only that owner's frames.
   */
  public synchronized ProfileDtos.Hot hot(BiFunction<String, String, FrameOwner> owners, String ownerId, int limit) {
    Map<String, MethodTally> tallies = new HashMap<>();
    Map<String, Integer> onPath = new HashMap<>();
    for (Node root : threads.values()) {
      for (int index = 0; index < root.count; index++) {
        hot(root.children[index], owners, ownerId, tallies, onPath);
      }
    }
    List<ProfileDtos.HotMethod> methods = new ArrayList<>(tallies.size());
    for (Map.Entry<String, MethodTally> entry : tallies.entrySet()) {
      MethodTally tally = entry.getValue();
      methods.add(new ProfileDtos.HotMethod(entry.getKey(), tally.owner.id(), tally.owner.name(), tally.self, tally.total,
          percent(tally.self, samples), percent(tally.total, samples)));
    }
    methods.sort(Comparator.comparingLong(ProfileDtos.HotMethod::self).reversed()
        .thenComparing(Comparator.comparingLong(ProfileDtos.HotMethod::total).reversed())
        .thenComparing(ProfileDtos.HotMethod::method));
    int kept = Math.max(0, Math.min(limit, methods.size()));
    return new ProfileDtos.Hot(samples, List.copyOf(methods.subList(0, kept)));
  }

  private void hot(Node node, BiFunction<String, String, FrameOwner> owners, String ownerId, Map<String, MethodTally> tallies,
                   Map<String, Integer> onPath) {
    FrameOwner owner = owners.apply(node.className, node.methodName);
    boolean counted = ownerId == null || ownerId.equals(owner.id());
    int depth = onPath.merge(node.method, 1, Integer::sum);
    if (counted) {
      MethodTally tally = tallies.computeIfAbsent(node.method, (String method) -> new MethodTally(owner));
      tally.self += node.self;
      if (depth == 1) {
        tally.total += node.total;
      }
    }
    for (int index = 0; index < node.count; index++) {
      hot(node.children[index], owners, ownerId, tallies, onPath);
    }
    if (depth == 1) {
      onPath.remove(node.method);
    } else {
      onPath.put(node.method, depth - 1);
    }
  }

  /**
   * The tree from each thread. With {@code ownerId}, only the paths through that owner's frames: everything under its
   * frames, and the callers above them with totals cut to those paths. Nodes under {@code minPercent} of all samples
   * fold into one {@link #OTHER} child; nodes deeper than {@code maxDepth} lose their children.
   */
  public synchronized ProfileDtos.Tree tree(BiFunction<String, String, FrameOwner> owners, String ownerId, String thread,
                                            double minPercent, int maxDepth) {
    long scope = 0;
    List<Node> roots = new ArrayList<>();
    for (Node root : threads.values()) {
      if (thread == null || thread.equals(root.method)) {
        roots.add(root);
        scope += root.total;
      }
    }
    long threshold = (long) Math.ceil(scope * Math.max(0D, minPercent) / 100D);
    List<Built> built = new ArrayList<>(roots.size());
    for (Node root : roots) {
      Built child = build(root, owners, ownerId, false, 0, Math.max(0, maxDepth));
      if (child != null) {
        built.add(child);
      }
    }
    return new ProfileDtos.Tree(scope, fold(built, threshold));
  }

  private Built build(Node node, BiFunction<String, String, FrameOwner> owners, String ownerId, boolean under, int depth,
                      int maxDepth) {
    FrameOwner owner = node.className == null ? null : owners.apply(node.className, node.methodName);
    boolean whole = ownerId == null || under || (owner != null && ownerId.equals(owner.id()));
    List<Built> children = new ArrayList<>();
    long total = 0;
    for (int index = 0; index < node.count; index++) {
      Built child = build(node.children[index], owners, ownerId, whole, depth + 1, maxDepth);
      if (child != null) {
        children.add(child);
        total += child.total;
      }
    }
    if (whole) {
      total = node.total;
    } else if (children.isEmpty()) {
      return null;
    }
    return new Built(node, owner, total, whole ? node.self : 0, depth < maxDepth ? children : new ArrayList<>());
  }

  private List<ProfileDtos.Node> fold(List<Built> built, long threshold) {
    built.sort(Comparator.comparingLong((Built child) -> child.total).reversed().thenComparing((Built child) -> child.node.method));
    List<ProfileDtos.Node> kept = new ArrayList<>(built.size());
    long folded = 0;
    for (Built child : built) {
      if (child.total < threshold) {
        folded += child.total;
        continue;
      }
      String ownerId = child.owner == null ? THREAD_OWNER : child.owner.id();
      String ownerName = child.owner == null ? "Thread" : child.owner.name();
      kept.add(new ProfileDtos.Node(child.node.method, ownerId, ownerName, child.total, child.self, fold(child.children, threshold)));
    }
    if (folded > 0) {
      kept.add(new ProfileDtos.Node(OTHER, "", "", folded, folded, List.of()));
    }
    return kept;
  }

  /** Each sampled thread with its last state, busiest first. */
  public synchronized ProfileDtos.Threads threads() {
    List<ProfileDtos.ThreadSamples> list = new ArrayList<>(threads.size());
    for (Node root : threads.values()) {
      list.add(new ProfileDtos.ThreadSamples(root.method, threadStates.getOrDefault(root.method, ""), root.total, percent(root.total, samples)));
    }
    list.sort(Comparator.comparingLong(ProfileDtos.ThreadSamples::samples).reversed().thenComparing(ProfileDtos.ThreadSamples::name));
    return new ProfileDtos.Threads(list);
  }

  static double percent(long part, long whole) {
    return whole <= 0 ? 0D : Math.round(part * 10_000D / whole) / 100D;
  }

  private static String display(String className, String methodName, int line) {
    return line >= 0 ? className + '.' + methodName + ':' + line : className + '.' + methodName;
  }

  private record Built(Node node, FrameOwner owner, long total, long self, List<Built> children) {
  }

  private static final class OwnerTally {
    private final FrameOwner owner;
    private final Map<String, Long> methods = new HashMap<>();
    private long samples;

    private OwnerTally(FrameOwner owner) {
      this.owner = owner;
    }
  }

  private static final class MethodTally {
    private final FrameOwner owner;
    private long self;
    private long total;

    private MethodTally(FrameOwner owner) {
      this.owner = owner;
    }
  }

  private record FrameKey(String className, String methodName, int line) {
  }

  private static final class Node {
    private final String className;
    private final String methodName;
    private final int line;
    private final String method;
    private long total;
    private long self;
    private Node[] children = NO_CHILDREN;
    private int count;
    private Map<FrameKey, Node> index;

    private Node(String className, String methodName, int line, String method) {
      this.className = className;
      this.methodName = methodName;
      this.line = line;
      this.method = method;
    }

    private Node child(String className, String methodName, int line) {
      if (index != null) {
        return index.get(new FrameKey(className, methodName, line));
      }
      for (int position = 0; position < count; position++) {
        Node child = children[position];
        if (child.line == line && child.methodName.equals(methodName) && child.className.equals(className)) {
          return child;
        }
      }
      return null;
    }

    private Node addChild(Node child) {
      if (count == children.length) {
        children = Arrays.copyOf(children, Math.max(2, count * 2));
      }
      children[count++] = child;
      if (index != null) {
        index.put(new FrameKey(child.className, child.methodName, child.line), child);
      } else if (count > INDEX_AT) {
        index = new HashMap<>();
        for (int position = 0; position < count; position++) {
          Node existing = children[position];
          index.put(new FrameKey(existing.className, existing.methodName, existing.line), existing);
        }
      }
      return child;
    }
  }
}
