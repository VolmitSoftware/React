package art.arcane.react.api.web.profiler;

import art.arcane.react.platform.ProfilerHost.FrameOwner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiFunction;

class CallTreeTest {
  private static final FrameOwner WORLDEDIT = new FrameOwner("WorldEdit", "WorldEdit", false);
  private static final FrameOwner REACT = new FrameOwner("React", "React", false);
  private static final BiFunction<String, String, FrameOwner> OWNERS = (String className, String methodName) -> {
    if (className.startsWith("com.sk89q.")) {
      return WORLDEDIT;
    }
    if (className.startsWith("art.arcane.react.")) {
      return REACT;
    }
    return className.startsWith("java.") ? FrameOwners.JAVA : FrameOwners.MINECRAFT;
  };

  private static StackTraceElement[] stack(String... frames) {
    StackTraceElement[] stack = new StackTraceElement[frames.length];
    for (int index = 0; index < frames.length; index++) {
      String frame = frames[frames.length - 1 - index];
      int dot = frame.lastIndexOf('.');
      stack[index] = new StackTraceElement(frame.substring(0, dot), frame.substring(dot + 1), null, index);
    }
    return stack;
  }

  private static CallTree sample() {
    CallTree tree = new CallTree(CallTree.DEFAULT_MAX_NODES, false);
    for (int index = 0; index < 6; index++) {
      tree.add("Server thread", Thread.State.RUNNABLE, stack("net.minecraft.Server.tick", "net.minecraft.Level.tick", "net.minecraft.Entity.tick"));
    }
    for (int index = 0; index < 3; index++) {
      tree.add("Server thread", Thread.State.RUNNABLE, stack("net.minecraft.Server.tick", "com.sk89q.Edit.run", "net.minecraft.Level.setBlock"));
    }
    tree.add("Server thread", Thread.State.RUNNABLE, stack("net.minecraft.Server.tick", "art.arcane.react.Sampler.sample", "java.util.HashMap.get"));
    return tree;
  }

  @Test
  void addCountsTotalOnThePathAndSelfAtTheLeaf() {
    CallTree tree = sample();
    ProfileDtos.Tree view = tree.tree(OWNERS, null, null, 0D, 64);
    Assertions.assertEquals(10, view.samples());
    ProfileDtos.Node thread = view.roots().get(0);
    Assertions.assertEquals("Server thread", thread.method());
    Assertions.assertEquals(10, thread.total());
    ProfileDtos.Node serverTick = thread.children().get(0);
    Assertions.assertEquals("net.minecraft.Server.tick", serverTick.method());
    Assertions.assertEquals(10, serverTick.total());
    Assertions.assertEquals(0, serverTick.self());
    ProfileDtos.Node levelTick = serverTick.children().get(0);
    Assertions.assertEquals(6, levelTick.total());
    Assertions.assertEquals(6, levelTick.children().get(0).self());
  }

  @Test
  void ownersChargeTheInnermostPluginFrameAndKeepReact() {
    ProfileDtos.Owners owners = sample().owners(OWNERS);
    Assertions.assertEquals(10, owners.samples());
    Assertions.assertEquals(List.of("minecraft", "WorldEdit", "React"), owners.owners().stream().map(ProfileDtos.OwnerRank::id).toList());
    ProfileDtos.OwnerRank worldEdit = owners.owners().get(1);
    Assertions.assertEquals(3, worldEdit.samples());
    Assertions.assertEquals(30D, worldEdit.percent());
    Assertions.assertEquals("com.sk89q.Edit.run", worldEdit.top().get(0).method());
    Assertions.assertEquals(1, owners.owners().get(2).samples());
    Assertions.assertEquals("net.minecraft.Entity.tick", owners.owners().get(0).top().get(0).method());
  }

  @Test
  void ownerTopListsAtMostThreeMethods() {
    CallTree tree = new CallTree(CallTree.DEFAULT_MAX_NODES, false);
    for (String method : List.of("a", "b", "c", "d")) {
      tree.add("main", Thread.State.RUNNABLE, stack("com.sk89q.Edit." + method));
    }
    Assertions.assertEquals(3, tree.owners(OWNERS).owners().get(0).top().size());
  }

  @Test
  void ownerFilteredTreeKeepsCallersAndCutsTheirTotals() {
    ProfileDtos.Tree view = sample().tree(OWNERS, "WorldEdit", null, 0D, 64);
    ProfileDtos.Node thread = view.roots().get(0);
    Assertions.assertEquals(3, thread.total());
    ProfileDtos.Node serverTick = thread.children().get(0);
    Assertions.assertEquals(1, serverTick.children().size());
    ProfileDtos.Node edit = serverTick.children().get(0);
    Assertions.assertEquals("com.sk89q.Edit.run", edit.method());
    Assertions.assertEquals("WorldEdit", edit.owner());
    Assertions.assertEquals("net.minecraft.Level.setBlock", edit.children().get(0).method());
  }

  @Test
  void ownerFilterWithoutFramesIsEmpty() {
    Assertions.assertTrue(sample().tree(OWNERS, "Nobody", null, 0D, 64).roots().isEmpty());
  }

  @Test
  void smallChildrenFoldIntoOther() {
    ProfileDtos.Tree view = sample().tree(OWNERS, null, null, 25D, 64);
    List<ProfileDtos.Node> children = view.roots().get(0).children().get(0).children();
    Assertions.assertEquals(List.of("net.minecraft.Level.tick", "com.sk89q.Edit.run", CallTree.OTHER),
        children.stream().map(ProfileDtos.Node::method).toList());
    ProfileDtos.Node other = children.get(2);
    Assertions.assertEquals(1, other.total());
    Assertions.assertEquals(1, other.self());
    Assertions.assertTrue(other.children().isEmpty());
  }

  @Test
  void maxDepthDropsDeeperChildren() {
    ProfileDtos.Node thread = sample().tree(OWNERS, null, null, 0D, 1).roots().get(0);
    Assertions.assertTrue(thread.children().get(0).children().isEmpty());
  }

  @Test
  void threadFilterAndThreadList() {
    CallTree tree = sample();
    tree.add("Netty", Thread.State.BLOCKED, stack("io.netty.Loop.run"));
    Assertions.assertEquals(1, tree.tree(OWNERS, null, "Netty", 0D, 64).samples());
    ProfileDtos.Threads threads = tree.threads();
    Assertions.assertEquals("Server thread", threads.threads().get(0).name());
    Assertions.assertEquals("BLOCKED", threads.threads().get(1).state());
    Assertions.assertEquals(9.09D, threads.threads().get(1).percent());
  }

  @Test
  void hotCountsRecursionOnceAndFiltersByOwner() {
    CallTree tree = new CallTree(CallTree.DEFAULT_MAX_NODES, false);
    tree.add("main", Thread.State.RUNNABLE, stack("com.sk89q.Edit.walk", "com.sk89q.Edit.walk", "net.minecraft.Level.get"));
    tree.add("main", Thread.State.RUNNABLE, stack("com.sk89q.Edit.walk"));
    ProfileDtos.Hot all = tree.hot(OWNERS, null, 50);
    Assertions.assertEquals("com.sk89q.Edit.walk", all.methods().get(0).method());
    Assertions.assertEquals(2, all.methods().get(0).total());
    Assertions.assertEquals(1, all.methods().get(0).self());
    ProfileDtos.Hot worldEdit = tree.hot(OWNERS, "WorldEdit", 50);
    Assertions.assertEquals(1, worldEdit.methods().size());
    Assertions.assertEquals(1, tree.hot(OWNERS, null, 1).methods().size());
  }

  @Test
  void nodeLimitKeepsSamplesAtTheDeepestExistingNode() {
    CallTree tree = new CallTree(3, false);
    tree.add("main", Thread.State.RUNNABLE, stack("a.A.one", "a.A.two", "a.A.three"));
    Assertions.assertEquals(3, tree.nodes());
    ProfileDtos.Node two = tree.tree(OWNERS, null, null, 0D, 64).roots().get(0).children().get(0).children().get(0);
    Assertions.assertEquals("a.A.two", two.method());
    Assertions.assertEquals(1, two.self());
  }

  @Test
  void depthIsCappedFromTheRoot() {
    CallTree tree = new CallTree(CallTree.DEFAULT_MAX_NODES, false);
    String[] frames = new String[CallTree.MAX_DEPTH + 10];
    for (int index = 0; index < frames.length; index++) {
      frames[index] = "a.A.f" + index;
    }
    tree.add("main", Thread.State.RUNNABLE, stack(frames));
    Assertions.assertEquals(CallTree.MAX_DEPTH + 1, tree.nodes());
  }

  @Test
  void lineNumbersSplitFramesWhenEnabled() {
    CallTree tree = new CallTree(CallTree.DEFAULT_MAX_NODES, true);
    tree.add("main", Thread.State.RUNNABLE, new StackTraceElement[] {new StackTraceElement("a.A", "f", null, 1)});
    tree.add("main", Thread.State.RUNNABLE, new StackTraceElement[] {new StackTraceElement("a.A", "f", null, 2)});
    Assertions.assertEquals(3, tree.nodes());
    Assertions.assertTrue(tree.tree(OWNERS, null, null, 0D, 64).roots().get(0).children().get(0).method().startsWith("a.A.f:"));
  }

  @Test
  void wideNodesStayFindableAfterIndexing() {
    CallTree tree = new CallTree(CallTree.DEFAULT_MAX_NODES, false);
    for (int round = 0; round < 2; round++) {
      for (int index = 0; index < 40; index++) {
        tree.add("main", Thread.State.RUNNABLE, stack("a.A.f" + index));
      }
    }
    Assertions.assertEquals(41, tree.nodes());
  }
}
