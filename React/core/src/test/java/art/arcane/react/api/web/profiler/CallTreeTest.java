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

  private static CallTree.Query query(String owner, String thread, double minPercent, int maxDepth) {
    return new CallTree.Query(owner, thread, minPercent, maxDepth, true, true, false);
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
    ProfileDtos.Tree view = tree.tree(OWNERS, query(null, null, 0D, 64));
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
    ProfileDtos.Owners owners = sample().owners(OWNERS, false);
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
    Assertions.assertEquals(3, tree.owners(OWNERS, false).owners().get(0).top().size());
  }

  @Test
  void ownerFilteredTreeKeepsCallersAndCutsTheirTotals() {
    ProfileDtos.Tree view = sample().tree(OWNERS, query("WorldEdit", null, 0D, 64));
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
    Assertions.assertTrue(sample().tree(OWNERS, query("Nobody", null, 0D, 64)).roots().isEmpty());
  }

  @Test
  void smallChildrenFoldIntoOther() {
    ProfileDtos.Tree view = sample().tree(OWNERS, query(null, null, 25D, 64));
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
    ProfileDtos.Node thread = sample().tree(OWNERS, query(null, null, 0D, 1)).roots().get(0);
    Assertions.assertTrue(thread.children().get(0).children().isEmpty());
  }

  @Test
  void threadFilterAndThreadList() {
    CallTree tree = sample();
    tree.add("Netty", Thread.State.BLOCKED, stack("io.netty.Loop.run"));
    Assertions.assertEquals(1, tree.tree(OWNERS, query(null, "Netty", 0D, 64)).samples());
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
    ProfileDtos.Hot all = tree.hot(OWNERS, null, 50, false);
    Assertions.assertEquals("com.sk89q.Edit.walk", all.methods().get(0).method());
    Assertions.assertEquals(2, all.methods().get(0).total());
    Assertions.assertEquals(1, all.methods().get(0).self());
    ProfileDtos.Hot worldEdit = tree.hot(OWNERS, "WorldEdit", 50, false);
    Assertions.assertEquals(1, worldEdit.methods().size());
    Assertions.assertEquals(1, tree.hot(OWNERS, null, 1, false).methods().size());
  }

  @Test
  void nodeLimitKeepsSamplesAtTheDeepestExistingNode() {
    CallTree tree = new CallTree(3, false);
    tree.add("main", Thread.State.RUNNABLE, stack("a.A.one", "a.A.two", "a.A.three"));
    Assertions.assertEquals(3, tree.nodes());
    ProfileDtos.Node two = tree.tree(OWNERS, query(null, null, 0D, 64)).roots().get(0).children().get(0).children().get(0);
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
    Assertions.assertTrue(tree.tree(OWNERS, query(null, null, 0D, 64)).roots().get(0).children().get(0).method().startsWith("a.A.f:"));
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

  @Test
  void idleLeavesAreLeftOutUnlessAsked() {
    CallTree tree = sample();
    for (int index = 0; index < 90; index++) {
      tree.add("Server thread", Thread.State.TIMED_WAITING, stack("net.minecraft.Server.tick", "net.minecraft.Server.waitForTasks",
          "java.util.concurrent.locks.LockSupport.parkNanos", "jdk.internal.misc.Unsafe.park"));
    }
    ProfileDtos.Tree busy = tree.tree(OWNERS, query(null, null, 0D, 64));
    Assertions.assertEquals(10, busy.samples());
    Assertions.assertEquals(90, busy.idleSamples());
    Assertions.assertEquals(1, busy.roots().get(0).children().get(0).children().stream()
        .filter((ProfileDtos.Node node) -> node.method().equals("net.minecraft.Level.tick")).count());
    Assertions.assertTrue(busy.roots().get(0).children().get(0).children().stream()
        .noneMatch((ProfileDtos.Node node) -> node.method().contains("waitForTasks")));
    ProfileDtos.Tree all = tree.tree(OWNERS, new CallTree.Query(null, null, 0D, 64, true, true, true));
    Assertions.assertEquals(100, all.samples());
    ProfileDtos.Owners owners = tree.owners(OWNERS, false);
    Assertions.assertEquals(10, owners.samples());
    Assertions.assertEquals(90, owners.idleSamples());
    Assertions.assertEquals(30D, owners.owners().get(1).percent());
    ProfileDtos.Hot hot = tree.hot(OWNERS, null, 50, false);
    Assertions.assertEquals(10, hot.samples());
    Assertions.assertTrue(hot.methods().stream().noneMatch((ProfileDtos.HotMethod method) -> method.method().contains("park")));
    Assertions.assertEquals(100, tree.hot(OWNERS, null, 50, true).samples());
  }

  @Test
  void threadsDifferingByIndexMergeIntoOneRoot() {
    CallTree tree = new CallTree(CallTree.DEFAULT_MAX_NODES, false);
    for (int worker = 1; worker <= 12; worker++) {
      tree.add("Worker-Main-" + worker, Thread.State.RUNNABLE, stack("net.minecraft.Chunk.generate", "net.minecraft.Noise.fill"));
    }
    tree.add("Server thread", Thread.State.RUNNABLE, stack("net.minecraft.Server.tick"));
    ProfileDtos.Tree grouped = tree.tree(OWNERS, query(null, null, 0D, 64));
    ProfileDtos.Node workers = grouped.roots().get(0);
    Assertions.assertEquals("Worker-Main (12)", workers.method());
    Assertions.assertEquals(12, workers.total());
    Assertions.assertEquals(12, workers.threads().size());
    Assertions.assertEquals(1, workers.children().size());
    Assertions.assertEquals(12, workers.children().get(0).children().get(0).self());
    Assertions.assertNull(grouped.roots().get(1).threads());
    Assertions.assertEquals(12, tree.tree(OWNERS, query(null, "Worker-Main (12)", 0D, 64)).samples());
    Assertions.assertEquals(1, tree.tree(OWNERS, query(null, "Worker-Main-7", 0D, 64)).samples());
    Assertions.assertEquals(13, tree.tree(OWNERS, new CallTree.Query(null, null, 0D, 64, false, true, false)).roots().size());
    Assertions.assertEquals("Worker-Main", CallTree.groupKey("Worker-Main-10"));
    Assertions.assertEquals("C2 CompilerThread", CallTree.groupKey("C2 CompilerThread0"));
    Assertions.assertEquals("Server thread", CallTree.groupKey("Server thread"));
  }

  @Test
  void plumbingRunsCollapseIntoOneNode() {
    CallTree tree = new CallTree(CallTree.DEFAULT_MAX_NODES, false);
    tree.add("Worker", Thread.State.RUNNABLE, stack("java.lang.Thread.run", "java.util.concurrent.ForkJoinWorkerThread.run",
        "java.util.concurrent.ForkJoinPool.runWorker", "java.util.concurrent.CompletableFuture$AsyncSupply.run",
        "net.minecraft.ChunkStatus.lambda$generate$3", "net.minecraft.Noise.fill"));
    ProfileDtos.Node root = tree.tree(OWNERS, query(null, null, 0D, 64)).roots().get(0);
    ProfileDtos.Node collapsed = root.children().get(0);
    Assertions.assertEquals("(5 frames)", collapsed.method());
    Assertions.assertEquals(List.of("java.lang.Thread.run", "java.util.concurrent.ForkJoinWorkerThread.run",
        "java.util.concurrent.ForkJoinPool.runWorker", "java.util.concurrent.CompletableFuture$AsyncSupply.run",
        "net.minecraft.ChunkStatus.lambda$generate$3"), collapsed.collapsed());
    Assertions.assertEquals("net.minecraft.Noise.fill", collapsed.children().get(0).method());
    ProfileDtos.Node expanded = tree.tree(OWNERS, new CallTree.Query(null, null, 0D, 64, true, false, false)).roots().get(0);
    Assertions.assertEquals("java.lang.Thread.run", expanded.children().get(0).method());
    Assertions.assertNull(expanded.children().get(0).collapsed());
  }

  @Test
  void plumbingWithSeveralBigChildrenEndsTheRun() {
    CallTree tree = new CallTree(CallTree.DEFAULT_MAX_NODES, false);
    tree.add("Worker", Thread.State.RUNNABLE, stack("java.lang.Thread.run", "java.util.concurrent.ForkJoinPool.runWorker", "a.A.one"));
    tree.add("Worker", Thread.State.RUNNABLE, stack("java.lang.Thread.run", "java.util.concurrent.ForkJoinPool.runWorker", "a.A.two"));
    tree.add("Worker", Thread.State.RUNNABLE, stack("java.lang.Thread.run", "java.util.concurrent.ThreadPoolExecutor.run", "a.A.three"));
    ProfileDtos.Node thread = tree.tree(OWNERS, query(null, null, 0D, 64)).roots().get(0);
    Assertions.assertEquals("java.lang.Thread.run", thread.children().get(0).method());
    ProfileDtos.Node folded = tree.tree(OWNERS, query(null, null, 50D, 64)).roots().get(0).children().get(0);
    Assertions.assertEquals("(2 frames)", folded.method());
    Assertions.assertEquals(3, folded.total());
  }

  @Test
  void framesUseThePlatformNames() {
    FrameNames names = new FrameNames(
        (String className) -> className.equals("net.minecraft.class_2806") ? "net.minecraft.world.level.chunk.ChunkStatus" : className,
        (String methodName) -> methodName.replace("method_12154", "generate").replace("m_280308_", "generate"));
    CallTree tree = new CallTree(CallTree.DEFAULT_MAX_NODES, false, names);
    tree.add("Server thread", Thread.State.RUNNABLE, stack("net.minecraft.class_2806.method_12154",
        "net.minecraft.class_2806$1.lambda$m_280308_$2", "net.minecraft.Level.handler$zza000$create$onTick",
        "net.minecraft.Level.md705abc$react$foo", "net.minecraft.Level.handler$zzb000$m_280308_", "net.minecraft.Level.mixinextras$bridge$m_280308_$190"));
    ProfileDtos.Node node = tree.tree(OWNERS, new CallTree.Query(null, null, 0D, 64, false, false, false)).roots().get(0).children().get(0);
    Assertions.assertEquals("net.minecraft.world.level.chunk.ChunkStatus.generate", node.method());
    node = node.children().get(0);
    Assertions.assertEquals("net.minecraft.world.level.chunk.ChunkStatus$1.lambda$generate$2", node.method());
    node = node.children().get(0);
    Assertions.assertEquals("<mixin create>.onTick", node.method());
    node = node.children().get(0);
    Assertions.assertEquals("<mixin react>.foo", node.method());
    node = node.children().get(0);
    Assertions.assertEquals("<mixin>.generate", node.method());
    Assertions.assertEquals("<mixin>.generate", node.children().get(0).method());
  }

  @Test
  void waitingFramesAreIdleAndWorkIsNot() {
    Assertions.assertTrue(FrameKinds.idle("jdk.internal.misc.Unsafe", "park"));
    Assertions.assertTrue(FrameKinds.idle("java.lang.Object", "wait0"));
    Assertions.assertTrue(FrameKinds.idle("java.lang.Thread", "sleep0"));
    Assertions.assertTrue(FrameKinds.idle("sun.nio.ch.KQueue", "poll"));
    Assertions.assertTrue(FrameKinds.idle("sun.nio.ch.Net", "accept"));
    Assertions.assertTrue(FrameKinds.idle("io.netty.channel.epoll.Native", "epollWait"));
    Assertions.assertTrue(FrameKinds.idle("java.lang.VirtualThread", "takeVirtualThreadListToUnblock"));
    Assertions.assertTrue(FrameKinds.idle("java.lang.invoke.LambdaForm$MH/0x000001e001220400", "invoke"));
    Assertions.assertFalse(FrameKinds.idle("java.util.HashMap", "get"));
    Assertions.assertFalse(FrameKinds.idle("net.minecraft.server.level.ServerLevel", "tick"));
    Assertions.assertTrue(FrameKinds.plumbing("net.minecraft.util.thread.ProcessorMailbox", "run"));
    Assertions.assertTrue(FrameKinds.plumbing("com.mojang.datafixers.util.Either$Left", "map"));
    Assertions.assertTrue(FrameKinds.plumbing("net.minecraft.server.level.ChunkMap", "lambda$scheduleChunkGeneration$1"));
    Assertions.assertFalse(FrameKinds.plumbing("net.minecraft.server.level.ChunkMap", "tick"));
  }
}
