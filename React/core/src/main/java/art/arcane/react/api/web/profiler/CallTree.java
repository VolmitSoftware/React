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
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sampled stacks merged into one call tree per thread. Each node counts the samples that passed through it (total) and
 * the samples that ended in it (self). Past {@code maxNodes} a sample stops at the deepest node that already exists, so
 * memory stays bounded and only the deepest detail is lost. Every method is synchronized: the sampler adds while web
 * threads read. Samples whose leaf is an idle frame ({@link FrameKinds#idle}) are kept but left out of queries unless
 * asked for.
 */
public final class CallTree {
  public static final int DEFAULT_MAX_NODES = 200_000;
  public static final int MAX_DEPTH = 256;
  public static final String OTHER = "(other)";
  public static final String THREAD_OWNER = "thread";
  private static final int INDEX_AT = 16;
  private static final Node[] NO_CHILDREN = new Node[0];
  private static final Pattern THREAD_INDEX = Pattern.compile("^(.*?\\S)[\\s\\-_#:.]*(?:\\d+|\\[\\d+]|\\(\\d+\\))$");
  private static final Pattern GROUP_LABEL = Pattern.compile("^(.*) \\(\\d+\\)$");

  private final int maxNodes;
  private final boolean lines;
  private final FrameNames names;
  private final Map<String, Node> threads = new LinkedHashMap<>();
  private final Map<String, String> threadStates = new HashMap<>();
  private final Set<String> classNames = new HashSet<>();
  private int nodes;
  private long samples;

  public CallTree(int maxNodes, boolean lines) {
    this(maxNodes, lines, FrameNames.IDENTITY);
  }

  public CallTree(int maxNodes, boolean lines, FrameNames names) {
    this.maxNodes = Math.max(1, maxNodes);
    this.lines = lines;
    this.names = names;
  }

  /** What a tree query keeps: one owner's paths, one thread or thread group, grouping, collapsing and idle samples. */
  public record Query(String ownerId, String thread, double minPercent, int maxDepth, boolean group, boolean collapse,
                      boolean idle) {
  }

  /** One sample of one thread; {@code stack[0]} is the innermost frame, as {@link Thread#getStackTrace()} returns it. */
  public synchronized void add(String thread, Thread.State state, StackTraceElement[] stack) {
    Node node = threads.get(thread);
    if (node == null) {
      if (nodes >= maxNodes) {
        return;
      }
      node = new Node(null, thread, -1, thread, false, false);
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
        child = node.addChild(newNode(className, methodName, line));
        classNames.add(className);
        nodes++;
      }
      child.total++;
      node = child;
    }
    node.self++;
  }

  private Node newNode(String className, String methodName, int line) {
    String readable = names.readableClass(className);
    String display = names.display(readable, methodName);
    if (line >= 0) {
      display = display + ':' + line;
    }
    return new Node(className, methodName, line, display, FrameKinds.idle(className, methodName),
        FrameKinds.plumbing(readable, names.readableMethod(methodName)));
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
  public synchronized ProfileDtos.Owners owners(BiFunction<String, String, FrameOwner> owners, boolean idle) {
    Map<String, OwnerTally> tallies = new HashMap<>();
    long active = 0;
    for (Node root : threads.values()) {
      active += rank(root, owners, idle, null, null, null, tallies);
    }
    List<ProfileDtos.OwnerRank> ranked = new ArrayList<>(tallies.size());
    for (OwnerTally tally : tallies.values()) {
      List<ProfileDtos.MethodCount> top = tally.methods.entrySet().stream()
          .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
          .limit(3)
          .map((Map.Entry<String, Long> entry) -> new ProfileDtos.MethodCount(entry.getKey(), entry.getValue()))
          .toList();
      ranked.add(new ProfileDtos.OwnerRank(tally.owner.id(), tally.owner.name(), tally.samples, percent(tally.samples, active), top));
    }
    ranked.sort(Comparator.comparingLong(ProfileDtos.OwnerRank::samples).reversed().thenComparing(ProfileDtos.OwnerRank::id));
    return new ProfileDtos.Owners(active, ranked, samples - active);
  }

  private long rank(Node node, BiFunction<String, String, FrameOwner> owners, boolean idle, FrameOwner charged,
                    String chargedMethod, String serverMethod, Map<String, OwnerTally> tallies) {
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
    long self = selfOf(node, idle);
    if (self > 0) {
      FrameOwner target = owner == null ? FrameOwners.MINECRAFT : owner;
      String top = owner == null ? server : method;
      OwnerTally tally = tallies.computeIfAbsent(target.id(), (String id) -> new OwnerTally(target));
      tally.samples += self;
      if (top != null) {
        tally.methods.merge(top, self, Long::sum);
      }
    }
    long active = self;
    for (int index = 0; index < node.count; index++) {
      active += rank(node.children[index], owners, idle, owner, method, server, tallies);
    }
    return active;
  }

  /**
   * Methods by self samples, then total. A method's total counts each sample once, however often it recurses. With an
   * {@code ownerId}, only that owner's frames.
   */
  public synchronized ProfileDtos.Hot hot(BiFunction<String, String, FrameOwner> owners, String ownerId, int limit, boolean idle) {
    Map<String, MethodTally> tallies = new HashMap<>();
    Map<String, Integer> onPath = new HashMap<>();
    long active = 0;
    for (Node root : threads.values()) {
      active += selfOf(root, idle);
      for (int index = 0; index < root.count; index++) {
        active += hot(root.children[index], owners, ownerId, idle, tallies, onPath);
      }
    }
    List<ProfileDtos.HotMethod> methods = new ArrayList<>(tallies.size());
    for (Map.Entry<String, MethodTally> entry : tallies.entrySet()) {
      MethodTally tally = entry.getValue();
      if (tally.total <= 0) {
        continue;
      }
      methods.add(new ProfileDtos.HotMethod(entry.getKey(), tally.owner.id(), tally.owner.name(), tally.self, tally.total,
          percent(tally.self, active), percent(tally.total, active)));
    }
    methods.sort(Comparator.comparingLong(ProfileDtos.HotMethod::self).reversed()
        .thenComparing(Comparator.comparingLong(ProfileDtos.HotMethod::total).reversed())
        .thenComparing(ProfileDtos.HotMethod::method));
    int kept = Math.max(0, Math.min(limit, methods.size()));
    return new ProfileDtos.Hot(active, List.copyOf(methods.subList(0, kept)), samples - active);
  }

  private long hot(Node node, BiFunction<String, String, FrameOwner> owners, String ownerId, boolean idle,
                   Map<String, MethodTally> tallies, Map<String, Integer> onPath) {
    FrameOwner owner = owners.apply(node.className, node.methodName);
    boolean counted = ownerId == null || ownerId.equals(owner.id());
    int depth = onPath.merge(node.method, 1, Integer::sum);
    long self = selfOf(node, idle);
    long subtree = self;
    for (int index = 0; index < node.count; index++) {
      subtree += hot(node.children[index], owners, ownerId, idle, tallies, onPath);
    }
    if (counted && subtree > 0) {
      MethodTally tally = tallies.computeIfAbsent(node.method, (String method) -> new MethodTally(owner));
      tally.self += self;
      if (depth == 1) {
        tally.total += subtree;
      }
    }
    if (depth == 1) {
      onPath.remove(node.method);
    } else {
      onPath.put(node.method, depth - 1);
    }
    return subtree;
  }

  /**
   * The tree from each thread, or each group of threads whose names differ only by a trailing index. With an owner,
   * only the paths through that owner's frames: everything under its frames, and the callers above them with totals
   * cut to those paths. Runs of plumbing frames collapse into one {@code "(N frames)"} node. Nodes under
   * {@code minPercent} of all samples fold into one {@link #OTHER} child; nodes deeper than {@code maxDepth} lose their
   * children.
   */
  public synchronized ProfileDtos.Tree tree(BiFunction<String, String, FrameOwner> owners, Query query) {
    long scope = 0;
    long raw = 0;
    Map<String, List<View>> groups = new LinkedHashMap<>();
    for (Node root : threads.values()) {
      if (!threadMatches(root.method, query.thread())) {
        continue;
      }
      raw += root.total;
      View view = view(root, query.idle());
      if (view == null) {
        continue;
      }
      scope += view.total;
      String key = query.group() ? groupKey(root.method) : root.method;
      groups.computeIfAbsent(key, (String name) -> new ArrayList<>()).add(view);
    }
    List<View> roots = new ArrayList<>(groups.size());
    for (Map.Entry<String, List<View>> group : groups.entrySet()) {
      List<View> members = group.getValue();
      View root = members.get(0);
      if (members.size() > 1) {
        Set<String> names = new TreeSet<>();
        for (View member : members) {
          names.add(member.node.method);
        }
        for (int index = 1; index < members.size(); index++) {
          merge(root, members.get(index));
        }
        root.label = group.getKey() + " (" + members.size() + ")";
        root.threads = List.copyOf(names);
      }
      roots.add(root);
    }
    long threshold = (long) Math.ceil(scope * Math.max(0D, query.minPercent()) / 100D);
    List<View> kept = new ArrayList<>(roots.size());
    for (View root : roots) {
      View pruned = prune(root, owners, query.ownerId(), false, 0, Math.max(0, query.maxDepth()));
      if (pruned != null) {
        if (query.collapse()) {
          collapseChildren(pruned, threshold);
        }
        kept.add(pruned);
      }
    }
    return new ProfileDtos.Tree(scope, fold(kept, threshold), raw - scope);
  }

  static String groupKey(String thread) {
    Matcher matcher = THREAD_INDEX.matcher(thread);
    return matcher.matches() ? matcher.group(1) : thread;
  }

  private static boolean threadMatches(String name, String wanted) {
    if (wanted == null || wanted.equals(name)) {
      return true;
    }
    String key = groupKey(name);
    if (wanted.equals(key)) {
      return true;
    }
    Matcher label = GROUP_LABEL.matcher(wanted);
    return label.matches() && label.group(1).equals(key);
  }

  private static long selfOf(Node node, boolean idle) {
    return !idle && node.idle ? 0 : node.self;
  }

  private static View view(Node node, boolean idle) {
    View view = new View(node);
    view.self = selfOf(node, idle);
    long total = view.self;
    for (int index = 0; index < node.count; index++) {
      View child = view(node.children[index], idle);
      if (child != null) {
        view.children.add(child);
        total += child.total;
      }
    }
    view.total = total;
    return total == 0 ? null : view;
  }

  private static void merge(View into, View from) {
    into.total += from.total;
    into.self += from.self;
    if (into.index == null) {
      into.index = new HashMap<>();
      for (View child : into.children) {
        into.index.put(child.node.key(), child);
      }
    }
    for (View child : from.children) {
      View existing = into.index.get(child.node.key());
      if (existing == null) {
        into.children.add(child);
        into.index.put(child.node.key(), child);
      } else {
        merge(existing, child);
      }
    }
  }

  private static View prune(View view, BiFunction<String, String, FrameOwner> owners, String ownerId, boolean under,
                            int depth, int maxDepth) {
    FrameOwner owner = view.node.className == null ? null : owners.apply(view.node.className, view.node.methodName);
    view.owner = owner;
    boolean whole = ownerId == null || under || (owner != null && ownerId.equals(owner.id()));
    if (whole && depth >= maxDepth) {
      view.children = new ArrayList<>();
      return view;
    }
    List<View> children = new ArrayList<>(view.children.size());
    long total = 0;
    for (View child : view.children) {
      View pruned = prune(child, owners, ownerId, whole, depth + 1, maxDepth);
      if (pruned != null) {
        children.add(pruned);
        total += pruned.total;
      }
    }
    if (!whole) {
      if (children.isEmpty()) {
        return null;
      }
      view.total = total;
      view.self = 0;
    }
    view.children = depth < maxDepth ? children : new ArrayList<>();
    return view;
  }

  private static void collapseChildren(View view, long threshold) {
    for (int index = 0; index < view.children.size(); index++) {
      View child = collapse(view.children.get(index), threshold);
      view.children.set(index, child);
      collapseChildren(child, threshold);
    }
  }

  /**
   * A run of plumbing frames, each with one child big enough to show, as one node. The run's smaller side branches,
   * which fold into {@link #OTHER} anyway, move under the collapsed node.
   */
  private static View collapse(View start, long threshold) {
    if (!start.node.plumbing) {
      return start;
    }
    List<View> run = new ArrayList<>();
    List<View> spill = new ArrayList<>();
    run.add(start);
    View current = start;
    while (true) {
      View next = null;
      int shown = 0;
      for (View child : current.children) {
        if (child.total >= threshold) {
          shown++;
          next = child;
        }
      }
      if (shown != 1 || !next.node.plumbing) {
        break;
      }
      for (View child : current.children) {
        if (child != next) {
          spill.add(child);
        }
      }
      run.add(next);
      current = next;
    }
    if (run.size() < 2) {
      return start;
    }
    View collapsed = new View(start.node);
    collapsed.owner = start.owner;
    collapsed.total = start.total;
    List<String> frames = new ArrayList<>(run.size());
    for (View frame : run) {
      collapsed.self += frame.self;
      frames.add(frame.node.method);
    }
    collapsed.collapsed = List.copyOf(frames);
    collapsed.children = new ArrayList<>(current.children);
    collapsed.children.addAll(spill);
    return collapsed;
  }

  private List<ProfileDtos.Node> fold(List<View> views, long threshold) {
    views.sort(Comparator.comparingLong((View child) -> child.total).reversed().thenComparing(View::label));
    List<ProfileDtos.Node> kept = new ArrayList<>(views.size());
    long folded = 0;
    for (View child : views) {
      if (child.total < threshold) {
        folded += child.total;
        continue;
      }
      String ownerId = child.owner == null ? THREAD_OWNER : child.owner.id();
      String ownerName = child.owner == null ? "Thread" : child.owner.name();
      kept.add(new ProfileDtos.Node(child.label(), ownerId, ownerName, child.total, child.self, fold(child.children, threshold),
          child.collapsed, child.threads));
    }
    if (folded > 0) {
      kept.add(new ProfileDtos.Node(OTHER, "", "", folded, folded, List.of(), null, null));
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

  private static final class View {
    private final Node node;
    private long total;
    private long self;
    private List<View> children = new ArrayList<>();
    private Map<FrameKey, View> index;
    private FrameOwner owner;
    private String label;
    private List<String> threads;
    private List<String> collapsed;

    private View(Node node) {
      this.node = node;
    }

    private String label() {
      if (collapsed != null) {
        return "(" + collapsed.size() + " frames)";
      }
      return label != null ? label : node.method;
    }
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
    private final boolean idle;
    private final boolean plumbing;
    private long total;
    private long self;
    private Node[] children = NO_CHILDREN;
    private int count;
    private Map<FrameKey, Node> index;

    private Node(String className, String methodName, int line, String method, boolean idle, boolean plumbing) {
      this.className = className;
      this.methodName = methodName;
      this.line = line;
      this.method = method;
      this.idle = idle;
      this.plumbing = plumbing;
    }

    private FrameKey key() {
      return new FrameKey(className, methodName, line);
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
        index.put(child.key(), child);
      } else if (count > INDEX_AT) {
        index = new HashMap<>();
        for (int position = 0; position < count; position++) {
          Node existing = children[position];
          index.put(existing.key(), existing);
        }
      }
      return child;
    }
  }
}
