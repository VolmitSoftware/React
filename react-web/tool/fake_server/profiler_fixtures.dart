// The fake fleet's sampling profiler and snapshots: a call tree that grows
// with the samples a running profiler would have taken, attributed to
// dozens of plugins (React among them), and frozen snapshots kept per
// server, newest first.
import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;

import 'fixtures.dart';

/// A plugin the fake servers load: its classes live under [package], and
/// [work] is how much of the server thread it takes (0 when disabled).
final class FakeOwnerSpec {
  const FakeOwnerSpec(
    this.id,
    this.version,
    this.package,
    this.work, {
    this.enabled = true,
    this.tasks = const <String>['task.SyncTask.run'],
    this.listeners = const <String>[],
    this.async = const <String>[],
  });

  final String id;
  final String version;
  final String package;
  final double work;
  final bool enabled;

  /// Scheduler tasks, `class.method` under [package].
  final List<String> tasks;

  /// Event handlers, `class.method` under [package].
  final List<String> listeners;

  /// Work on the plugin's own threads.
  final List<String> async;

  String get name => id;
}

const List<FakeOwnerSpec> fakeOwnerSpecs = <FakeOwnerSpec>[
  FakeOwnerSpec(
    'React',
    '5.0.0',
    'art.arcane.react',
    0.9,
    tasks: <String>[
      'core.controller.SampleController.tick',
      'content.feature.FeatureEntityStacker.scan',
    ],
    listeners: <String>['core.controller.EventCostController.onEvent'],
    async: <String>[
      'web.WebServer.handle',
      'core.telemetry.SelfTelemetry.flush',
    ],
  ),
  FakeOwnerSpec(
    'Iris',
    '4.0.0',
    'art.arcane.iris',
    3.4,
    tasks: <String>[
      'engine.framework.EngineMantle.update',
      'core.pregenerator.IrisPregenerator.tick',
    ],
    listeners: <String>['engine.platform.BukkitChunkGenerator.onChunkLoad'],
    async: <String>[
      'engine.IrisEngine.generate',
      'engine.mantle.MantleWriter.flush',
      'util.noise.CNG.noise',
    ],
  ),
  FakeOwnerSpec(
    'WorldEdit',
    '7.3.10',
    'com.sk89q.worldedit',
    0.7,
    tasks: <String>['bukkit.BukkitServerInterface.tick'],
    async: <String>['extent.reorder.MultiStageReorder.commit'],
  ),
  FakeOwnerSpec(
    'Essentials',
    '2.21.0',
    'com.earth2me.essentials',
    0,
    enabled: false,
  ),
  FakeOwnerSpec(
    'LuckPerms',
    '5.4.141',
    'me.lucko.luckperms',
    0.5,
    listeners: <String>[
      'bukkit.listeners.BukkitConnectionListener.onPlayerLogin',
    ],
    async: <String>['common.cacheddata.AbstractCachedDataManager.calculate'],
  ),
  FakeOwnerSpec(
    'Vault',
    '1.7.3',
    'net.milkbowl.vault',
    0.1,
    listeners: <String>['economy.plugins.Economy_Essentials.getBalance'],
  ),
  FakeOwnerSpec(
    'PlaceholderAPI',
    '2.11.6',
    'me.clip.placeholderapi',
    0.6,
    tasks: <String>['PlaceholderAPI.setPlaceholders'],
  ),
  FakeOwnerSpec(
    'ProtocolLib',
    '5.3.0',
    'com.comphenix.protocol',
    0.8,
    listeners: <String>[
      'injector.netty.channel.NettyChannelInjector.processOutbound',
    ],
    async: <String>['async.AsyncListenerHandler.processPacket'],
  ),
  FakeOwnerSpec(
    'CoreProtect',
    '22.4',
    'net.coreprotect',
    1.2,
    listeners: <String>[
      'listener.block.BlockBreakListener.onBlockBreak',
      'listener.player.PlayerInteractListener.onPlayerInteract',
    ],
    async: <String>['consumer.Consumer.run', 'database.Database.commit'],
  ),
  FakeOwnerSpec(
    'WorldGuard',
    '7.0.12',
    'com.sk89q.worldguard',
    1.6,
    listeners: <String>[
      'bukkit.listener.WorldGuardPlayerListener.onPlayerMove',
      'bukkit.listener.EventAbstractionListener.onBlockPhysics',
    ],
  ),
  FakeOwnerSpec(
    'Citizens',
    '2.0.35',
    'net.citizensnpcs',
    1.9,
    tasks: <String>['npc.CitizensNPC.update', 'npc.ai.CitizensNavigator.run'],
  ),
  FakeOwnerSpec(
    'Multiverse-Core',
    '4.3.14',
    'com.onarandombox.MultiverseCore',
    0.2,
    listeners: <String>['listeners.MVPlayerListener.playerTeleport'],
  ),
  FakeOwnerSpec(
    'dynmap',
    '3.7',
    'org.dynmap',
    1.1,
    tasks: <String>['bukkit.DynmapPlugin.processTick'],
    async: <String>[
      'MapManager.FullWorldRenderState.run',
      'hdmap.IsoHDPerspective.render',
    ],
  ),
  FakeOwnerSpec(
    'GriefPrevention',
    '16.18.4',
    'me.ryanhamshire.GriefPrevention',
    0.7,
    listeners: <String>[
      'BlockEventHandler.onBlockPistonExtend',
      'EntityEventHandler.onEntityDamage',
    ],
  ),
  FakeOwnerSpec(
    'mcMMO',
    '2.2.024',
    'com.gmail.nossr50',
    1.4,
    listeners: <String>[
      'listeners.BlockListener.onBlockBreak',
      'listeners.EntityListener.onEntityDamageByEntity',
    ],
    tasks: <String>['runnables.SaveTimerTask.run'],
  ),
  FakeOwnerSpec(
    'ViaVersion',
    '5.1.1',
    'com.viaversion.viaversion',
    0.9,
    async: <String>['protocol.ProtocolPipelineImpl.transform'],
  ),
  FakeOwnerSpec(
    'TAB',
    '4.1.9',
    'me.neznamy.tab',
    0.6,
    tasks: <String>['shared.features.layout.LayoutManagerImpl.tick'],
    async: <String>['shared.cpu.CpuManager.runMeasuredTask'],
  ),
  FakeOwnerSpec(
    'DiscordSRV',
    '1.28.0',
    'github.scarsz.discordsrv',
    0.3,
    listeners: <String>['listeners.PlayerChatListener.onAsyncPlayerChat'],
    async: <String>['dependencies.jda.internal.requests.Requester.execute'],
  ),
  FakeOwnerSpec(
    'MythicMobs',
    '5.7.2',
    'io.lumine.mythic',
    2.3,
    tasks: <String>[
      'core.mobs.MobExecutor.tick',
      'core.skills.SkillExecutor.runSkill',
    ],
    listeners: <String>['core.spawning.spawners.SpawnerManager.onChunkLoad'],
  ),
  FakeOwnerSpec(
    'ModelEngine',
    '4.0.8',
    'com.ticxo.modelengine',
    1.5,
    tasks: <String>['core.model.ActiveModelImpl.tick'],
    async: <String>['core.render.ModelRenderer.sendPackets'],
  ),
  FakeOwnerSpec(
    'Jobs',
    '5.2.4.4',
    'com.gamingmesh.jobs',
    0.4,
    listeners: <String>['listeners.JobsPaymentListener.onBlockBreak'],
  ),
  FakeOwnerSpec(
    'Chunky',
    '1.4.28',
    'org.popcraft.chunky',
    0.8,
    tasks: <String>['GenerationTask.run'],
  ),
  FakeOwnerSpec('HeadDatabase', '4.21.0', 'me.arcaniax.hdb', 0.1),
  FakeOwnerSpec(
    'DecentHolograms',
    '2.8.12',
    'eu.decentsoftware.holograms',
    0.5,
    tasks: <String>['api.holograms.HologramManager.tick'],
  ),
  FakeOwnerSpec(
    'Votifier',
    '2.7.3',
    'com.vexsoftware.votifier',
    0.05,
    async: <String>['net.VotifierSession.read'],
  ),
  FakeOwnerSpec(
    'SkinsRestorer',
    '15.4.4',
    'net.skinsrestorer',
    0.15,
    listeners: <String>['bukkit.listener.PlayerJoinListener.onJoin'],
  ),
  FakeOwnerSpec(
    'Geyser-Spigot',
    '2.5.0',
    'org.geysermc.geyser',
    0.9,
    async: <String>[
      'session.GeyserSession.tick',
      'translator.protocol.java.JavaLevelChunkTranslator.translate',
    ],
  ),
  FakeOwnerSpec('floodgate', '2.2.3', 'org.geysermc.floodgate', 0.05),
  FakeOwnerSpec(
    'BlueMap',
    '5.4',
    'de.bluecolored.bluemap',
    0.6,
    async: <String>[
      'common.rendermanager.RenderManager.renderTask',
      'core.map.hires.HiresModelManager.render',
    ],
  ),
  FakeOwnerSpec(
    'Quests',
    '3.15.2',
    'com.leonardobishop.quests',
    0.35,
    listeners: <String>['bukkit.tasktype.type.MiningTaskType.onBlockBreak'],
  ),
  FakeOwnerSpec('ShopGUIPlus', '1.97.1', 'net.brcdev.shopgui', 0.2),
  FakeOwnerSpec(
    'ItemsAdder',
    '4.0.4',
    'dev.lone.itemsadder',
    1.0,
    tasks: <String>['main.Hud.tick'],
    listeners: <String>['main.CustomBlocksListener.onBlockPhysics'],
  ),
];

const String _minecraft = 'minecraft';
const String _java = 'java';

/// One frame of the fake tree while it is built: weights, not samples.
final class _Shape {
  _Shape(this.method, this.owner, [this.weight = 0]);

  final String method;
  final String owner;

  /// The share of samples spent in this frame itself.
  double weight;
  final List<_Shape> children = <_Shape>[];

  _Shape child(String method, String owner, [double weight = 0]) {
    for (final _Shape existing in children) {
      if (existing.method == method) {
        existing.weight += weight;
        return existing;
      }
    }
    final _Shape created = _Shape(method, owner, weight);
    children.add(created);
    return created;
  }

  _Shape path(List<(String, String)> frames, double weight) {
    _Shape node = this;
    for (final (String method, String owner) in frames) {
      node = node.child(method, owner);
    }
    node.weight += weight;
    return node;
  }
}

/// One frame with sample counts, as the routes send it.
final class FakeFrame {
  FakeFrame(this.method, this.owner, this.self, this.children);

  final String method;
  final String owner;
  final int self;
  final List<FakeFrame> children;
  late final int total = children.fold(
    self,
    (int sum, FakeFrame child) => sum + child.total,
  );
}

final class FakeProfiler {
  FakeProfiler(this.profile);

  final FakeServerProfile profile;

  bool running = false;
  int intervalMs = 10;
  String threads = 'server';
  int maxSeconds = 600;
  DateTime? startedAt;
  DateTime? stopsAt;

  /// Samples taken by earlier runs since the last reset.
  int _banked = 0;
  int _bankedMs = 0;

  /// Stops a run whose time is up, as the server's auto-stop does.
  void _settle(DateTime now) {
    final DateTime? stops = stopsAt;
    if (running && stops != null && !now.isBefore(stops)) _stop(stops);
  }

  int _threadCount() => threads == 'all' ? _threadNames.length : 1;

  int _runMs(DateTime now) {
    final DateTime? started = startedAt;
    if (!running || started == null) return 0;
    return now.difference(started).inMilliseconds.clamp(0, maxSeconds * 1000);
  }

  int samples(DateTime now) {
    _settle(now);
    return _banked + _runMs(now) ~/ intervalMs * _threadCount();
  }

  int durationMs(DateTime now) {
    _settle(now);
    return _bankedMs + _runMs(now);
  }

  void _stop(DateTime at) {
    if (!running) return;
    _banked += _runMs(at) ~/ intervalMs * _threadCount();
    _bankedMs += _runMs(at);
    running = false;
    stopsAt = null;
  }

  Map<String, Object?> status(DateTime now) => <String, Object?>{
    'running': running && (stopsAt == null || now.isBefore(stopsAt!)),
    'intervalMs': intervalMs,
    'threads': threads,
    'startedAt': startedAt?.millisecondsSinceEpoch,
    'stopsAt': running ? stopsAt?.millisecondsSinceEpoch : null,
    'samples': samples(now),
    'durationMs': durationMs(now),
  };

  /// `POST /profiler`. Null when the body is not a command.
  Map<String, Object?>? apply(Map<String, dynamic> body, DateTime now) {
    _settle(now);
    switch (body['action']) {
      case 'start':
        final Object? interval = body['intervalMs'];
        final Object? seconds = body['maxSeconds'];
        final Object? which = body['threads'];
        if (interval != null && interval is! int ||
            seconds != null && seconds is! int ||
            which != null && which != 'server' && which != 'all') {
          return null;
        }
        _stop(now);
        _banked = 0;
        _bankedMs = 0;
        intervalMs = ((interval as int?) ?? 10).clamp(1, 1000);
        maxSeconds = ((seconds as int?) ?? 600).clamp(1, 3600);
        threads = (which as String?) ?? 'server';
        running = true;
        startedAt = now;
        stopsAt = now.add(Duration(seconds: maxSeconds));
      case 'stop':
        _stop(now);
      case 'reset':
        _banked = 0;
        _bankedMs = 0;
        if (running) {
          startedAt = now;
          stopsAt = now.add(Duration(seconds: maxSeconds));
        } else {
          startedAt = null;
        }
      default:
        return null;
    }
    return status(now);
  }

  static final List<(String, String)> _threadNames = <(String, String)>[
    ('Server thread', 'RUNNABLE'),
    ('Netty Epoll Server IO #0', 'RUNNABLE'),
    ('Netty Epoll Server IO #1', 'RUNNABLE'),
    for (int worker = 1; worker <= 12; worker++)
      ('Worker-Main-$worker', worker % 3 == 0 ? 'WAITING' : 'RUNNABLE'),
    ('Chunk I/O Executor', 'TIMED_WAITING'),
    ('Craft Scheduler Thread - 4', 'TIMED_WAITING'),
    ('Craft Scheduler Thread - 7', 'RUNNABLE'),
    ('Iris Mantle Writer', 'RUNNABLE'),
    ('React Web', 'RUNNABLE'),
    ('Async Chat Thread - #2', 'WAITING'),
    ('dynmap-render', 'RUNNABLE'),
  ];

  /// How much a plugin works this moment: its base share, swaying slowly
  /// with the samples so the live view moves and a stopped one holds still.
  double _sway(FakeOwnerSpec owner, int index, int samples) {
    final double phase = samples / 800 + index * 1.7 + profile.index;
    final double stress = profile.isCritical
        ? 1.8
        : (profile.isStressed ? 1.3 : 1);
    final double bias = index == profile.index * 3 % fakeOwnerSpecs.length
        ? 2.5
        : 1;
    return owner.work * stress * bias * (1 + 0.35 * math.sin(phase));
  }

  static String _class(String method) =>
      method.substring(0, method.lastIndexOf('.'));

  List<_Shape> _shapes(int samples) {
    final _Shape main = _Shape('Server thread', _minecraft);
    const String server = 'net.minecraft.server.MinecraftServer';
    final _Shape run = main.child('$server.runServer', _minecraft);
    run
        .child('$server.waitUntilNextTick', _minecraft)
        .child('java.util.concurrent.locks.LockSupport.parkNanos', _java, 9);
    final _Shape tick = run.child('$server.tickServer', _minecraft, 0.4);
    final _Shape children = tick.child('$server.tickChildren', _minecraft, 0.3);
    final _Shape level = children.child(
      'net.minecraft.server.level.ServerLevel.tick',
      _minecraft,
      0.5,
    );
    level
        .child(
          'net.minecraft.server.level.ServerChunkCache.tick',
          _minecraft,
          1.2,
        )
        .child('net.minecraft.server.level.ChunkMap.tick', _minecraft, 2.1)
        .child(
          'net.minecraft.world.level.chunk.LevelChunk.postProcessGeneration',
          _minecraft,
          0.7,
        );
    final _Shape entities = level
        .child(
          'net.minecraft.world.level.entity.EntityTickList.forEach',
          _minecraft,
          0.2,
        )
        .child(
          'net.minecraft.server.level.ServerLevel.tickNonPassenger',
          _minecraft,
          0.6,
        );
    entities
        .child('net.minecraft.world.entity.Mob.aiStep', _minecraft, 2.8)
        .child(
          'net.minecraft.world.entity.ai.goal.GoalSelector.tick',
          _minecraft,
          1.9,
        )
        .child(
          'net.minecraft.world.entity.ai.navigation.PathNavigation.createPath',
          _minecraft,
          1.4,
        );
    entities
        .child(
          'net.minecraft.world.entity.item.ItemEntity.tick',
          _minecraft,
          0.9,
        )
        .child('java.util.ArrayList.forEach', _java, 0.3);
    level.child(
      'net.minecraft.world.level.redstone.NeighborUpdater.updateNeighborsAtExceptFromFacing',
      _minecraft,
      0.8,
    );
    children
        .child(
          'net.minecraft.server.network.ServerConnectionListener.tick',
          _minecraft,
          0.4,
        )
        .child('net.minecraft.network.Connection.tick', _minecraft, 0.6);
    final _Shape heartbeat = children.child(
      'org.bukkit.craftbukkit.scheduler.CraftScheduler.mainThreadHeartbeat',
      _minecraft,
      0.1,
    );
    final _Shape events = entities
        .child(
          'org.bukkit.craftbukkit.event.CraftEventFactory.callEvent',
          _minecraft,
        )
        .child(
          'org.bukkit.plugin.SimplePluginManager.callEvent',
          _minecraft,
          0.05,
        )
        .child(
          'org.bukkit.plugin.RegisteredListener.callEvent',
          _minecraft,
          0.05,
        );
    _deepServerThread(level, entities, samples);
    final Map<String, _Shape> workers = <String, _Shape>{
      for (final (String name, String _) in _threadNames.skip(1))
        name: _Shape(name, _minecraft),
    };
    _Shape thread(String name) => workers[name]!;
    thread('Netty Epoll Server IO #0').path(<(String, String)>[
      ('io.netty.util.concurrent.FastThreadLocalRunnable.run', _minecraft),
      ('io.netty.util.internal.ThreadExecutorMap\$2.run', _minecraft),
      ('io.netty.util.concurrent.SingleThreadEventExecutor\$4.run', _minecraft),
      ('io.netty.channel.epoll.EpollEventLoop.run', _minecraft),
      ('io.netty.channel.epoll.Native.epollWait', _minecraft),
    ], 6);
    for (final String name in <String>[
      'Netty Epoll Server IO #0',
      'Netty Epoll Server IO #1',
    ]) {
      thread(name).path(<(String, String)>[
        ('io.netty.util.concurrent.FastThreadLocalRunnable.run', _minecraft),
        ('io.netty.util.internal.ThreadExecutorMap\$2.run', _minecraft),
        (
          'io.netty.util.concurrent.SingleThreadEventExecutor\$4.run',
          _minecraft,
        ),
        ('io.netty.channel.epoll.EpollEventLoop.run', _minecraft),
        ('io.netty.channel.epoll.EpollEventLoop.processReady', _minecraft),
        (
          'io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead',
          _minecraft,
        ),
        ('net.minecraft.network.PacketEncoder.encode', _minecraft),
        ('java.util.zip.Deflater.deflate', _java),
      ], name.endsWith('#1') ? 2.5 : 1.4);
    }
    final double iris = _ownerWork('Iris', samples);
    for (int worker = 1; worker <= 12; worker++) {
      _forgeWorker(thread('Worker-Main-$worker'), worker, iris);
    }
    thread('Chunk I/O Executor').path(<(String, String)>[
      ('java.lang.Thread.run', _java),
      ('net.minecraft.util.thread.ProcessorMailbox.run', _minecraft),
      ('net.minecraft.util.thread.ProcessorMailbox.pollTask', _minecraft),
      (
        'net.minecraft.world.level.chunk.storage.IOWorker.storePendingChunk',
        _minecraft,
      ),
      ('net.minecraft.nbt.NbtIo.write', _minecraft),
      ('java.io.DataOutputStream.write', _java),
    ], 1.2);
    thread('Chunk I/O Executor').path(<(String, String)>[
      ('java.lang.Thread.run', _java),
      ('net.minecraft.util.thread.ProcessorMailbox.run', _minecraft),
      ('java.util.concurrent.locks.LockSupport.parkNanos', _java),
    ], 2);
    final List<_Shape> asyncThreads = <_Shape>[
      thread('Craft Scheduler Thread - 4'),
      thread('Craft Scheduler Thread - 7'),
    ];
    for (final (int index, FakeOwnerSpec owner) in fakeOwnerSpecs.indexed) {
      if (!owner.enabled || owner.work <= 0) continue;
      final double work = _sway(owner, index, samples);
      final String pkg = owner.package;
      for (final String task in owner.tasks) {
        final String frame = '$pkg.$task';
        heartbeat
            .child(
              'org.bukkit.craftbukkit.scheduler.CraftTask.run',
              _minecraft,
              0.02,
            )
            .child(frame, owner.id, work * 0.35 / owner.tasks.length)
            .child(
              '${_class(frame)}.process',
              owner.id,
              work * 0.4 / owner.tasks.length,
            )
            .child(
              'java.util.HashMap.get',
              _java,
              work * 0.08 / owner.tasks.length,
            );
      }
      for (final String listener in owner.listeners) {
        final String frame = '$pkg.$listener';
        events
            .child(frame, owner.id, work * 0.3 / owner.listeners.length)
            .child(
              'org.bukkit.craftbukkit.block.CraftBlock.getType',
              _minecraft,
              work * 0.1 / owner.listeners.length,
            );
      }
      if (owner.tasks.isEmpty &&
          owner.listeners.isEmpty &&
          owner.async.isEmpty) {
        heartbeat.child('$pkg.task.SyncTask.run', owner.id, work * 0.5);
      }
      for (final (int at, String job) in owner.async.indexed) {
        final _Shape runs = owner.id == 'Iris'
            ? thread('Iris Mantle Writer')
            : owner.id == 'React'
            ? thread('React Web')
            : owner.id == 'dynmap'
            ? thread('dynmap-render')
            : asyncThreads[(index + at) % asyncThreads.length];
        runs
            .child('java.lang.Thread.run', _java)
            .child('java.util.concurrent.ThreadPoolExecutor\$Worker.run', _java)
            .child('java.util.concurrent.ThreadPoolExecutor.runWorker', _java)
            .child('$pkg.$job', owner.id, work * 0.6 / owner.async.length)
            .child('java.lang.String.hashCode', _java, work * 0.05);
      }
    }
    for (final _Shape idle in asyncThreads) {
      idle.path(<(String, String)>[
        ('java.lang.Thread.run', _java),
        ('java.util.concurrent.ThreadPoolExecutor\$Worker.run', _java),
        ('java.util.concurrent.ThreadPoolExecutor.runWorker', _java),
        ('java.util.concurrent.ThreadPoolExecutor.getTask', _java),
        ('java.util.concurrent.SynchronousQueue.poll', _java),
        ('java.util.concurrent.locks.LockSupport.parkNanos', _java),
      ], 1.5);
    }
    thread('React Web').path(<(String, String)>[
      ('java.lang.Thread.run', _java),
      ('java.util.concurrent.locks.LockSupport.park', _java),
    ], 2);
    thread('Async Chat Thread - #2').path(<(String, String)>[
      ('java.lang.Thread.run', _java),
      ('java.lang.Object.wait', _java),
    ], 1);
    return <_Shape>[main, ...workers.values];
  }

  /// How hard [id] works right now, 0 when it is not loaded.
  double _ownerWork(String id, int samples) {
    for (final (int index, FakeOwnerSpec owner) in fakeOwnerSpecs.indexed) {
      if (owner.id == id) {
        return owner.enabled ? _sway(owner, index, samples) : 0;
      }
    }
    return 0;
  }

  /// The server thread past the tick loop: block entities and the deeper
  /// entity AI, with plugin frames inside.
  void _deepServerThread(_Shape level, _Shape entities, int samples) {
    final _Shape blockEntities = level
        .child('net.minecraft.world.level.Level.tickBlockEntities', _minecraft)
        .child(
          'net.minecraft.world.level.chunk.LevelChunk\$RebindableTickingBlockEntityWrapper.tick',
          _minecraft,
        )
        .child(
          'net.minecraft.world.level.chunk.LevelChunk\$BoundTickingBlockEntity.tick',
          _minecraft,
          0.2,
        );
    blockEntities.path(<(String, String)>[
      (
        'net.minecraft.world.level.block.entity.HopperBlockEntity.pushItemsTick',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.block.entity.HopperBlockEntity.tryMoveItems',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.block.entity.HopperBlockEntity.suckInItems',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.block.entity.HopperBlockEntity.getItemsAtAndAbove',
        _minecraft,
      ),
      ('net.minecraft.world.level.Level.getEntities', _minecraft),
      (
        'net.minecraft.world.level.entity.LevelEntityGetterAdapter.get',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.entity.EntitySectionStorage.getEntities',
        _minecraft,
      ),
      ('net.minecraft.util.ClassInstanceMultiMap.find', _minecraft),
    ], 1.3);
    final double items = _ownerWork('ItemsAdder', samples);
    if (items > 0) {
      blockEntities.path(<(String, String)>[
        (
          'net.minecraft.world.level.block.entity.BlockEntityTicker.tick',
          _minecraft,
        ),
        ('dev.lone.itemsadder.main.CustomFurnace.tick', 'ItemsAdder'),
        ('dev.lone.itemsadder.main.CustomFurnace.updateRecipe', 'ItemsAdder'),
        ('java.util.HashMap.get', _java),
      ], items * 0.4);
    }
    final _Shape goals = entities
        .child('net.minecraft.world.entity.Mob.aiStep', _minecraft)
        .child('net.minecraft.world.entity.LivingEntity.aiStep', _minecraft)
        .child('net.minecraft.world.entity.Mob.serverAiStep', _minecraft, 0.4)
        .child(
          'net.minecraft.world.entity.ai.goal.GoalSelector.tickRunningGoals',
          _minecraft,
          0.3,
        )
        .child(
          'net.minecraft.world.entity.ai.goal.WrappedGoal.tick',
          _minecraft,
        );
    goals.path(<(String, String)>[
      ('net.minecraft.world.entity.ai.goal.MoveToBlockGoal.tick', _minecraft),
      (
        'net.minecraft.world.entity.ai.navigation.PathNavigation.moveTo',
        _minecraft,
      ),
      (
        'net.minecraft.world.entity.ai.navigation.PathNavigation.createPath',
        _minecraft,
      ),
      ('net.minecraft.world.level.pathfinder.PathFinder.findPath', _minecraft),
      (
        'net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getNeighbors',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.pathfinder.WalkNodeEvaluator.findAcceptedNode',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getBlockPathType',
        _minecraft,
      ),
      ('net.minecraft.world.level.Level.getBlockState', _minecraft),
    ], 2.2);
    final double mythic = _ownerWork('MythicMobs', samples);
    if (mythic > 0) {
      goals.path(<(String, String)>[
        (
          'io.lumine.mythic.bukkit.adapters.ai.PathfinderAdapter.tick',
          'MythicMobs',
        ),
        (
          'io.lumine.mythic.core.mobs.ai.goals.GoalFollowTarget.tick',
          'MythicMobs',
        ),
        ('io.lumine.mythic.core.skills.SkillTrigger.run', 'MythicMobs'),
      ], mythic * 0.3);
    }
  }

  /// One chunk worker of a Forge-style pool: the ForkJoin and mailbox
  /// plumbing, then noise, features and light down to the leaves, Iris
  /// generating inside it, and the parked idle time.
  static void _forgeWorker(_Shape worker, int index, double iris) {
    final double sway = 1 + 0.18 * math.sin(index * 1.3);
    const List<(String, String)> pool = <(String, String)>[
      ('java.util.concurrent.ForkJoinWorkerThread.run', _java),
      ('java.util.concurrent.ForkJoinPool.runWorker', _java),
    ];
    const List<(String, String)> task = <(String, String)>[
      ...pool,
      ('java.util.concurrent.ForkJoinPool.scan', _java),
      ('java.util.concurrent.ForkJoinTask.doExec', _java),
      ('java.util.concurrent.CompletableFuture\$AsyncSupply.exec', _java),
      ('java.util.concurrent.CompletableFuture\$AsyncSupply.run', _java),
      ('net.minecraft.util.thread.ProcessorMailbox.run', _minecraft),
      ('net.minecraft.util.thread.ProcessorMailbox.pollTask', _minecraft),
      (
        'net.minecraft.util.thread.StrictQueue\$QueueStrictQueue.pop',
        _minecraft,
      ),
      (
        'net.minecraft.server.level.ChunkMap.lambda\$scheduleChunkGeneration\$27',
        _minecraft,
      ),
      ('net.minecraft.world.level.chunk.ChunkStatus.generate', _minecraft),
      (
        'net.minecraft.world.level.chunk.ChunkStatus\$SimpleGenerationTask.doWork',
        _minecraft,
      ),
    ];
    worker.path(<(String, String)>[
      ...task,
      (
        'net.minecraft.world.level.chunk.ChunkStatus.lambda\$static\$9',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator.fillFromNoise',
        _minecraft,
      ),
      ('java.util.concurrent.CompletableFuture.supplyAsync', _java),
      (
        'net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator.lambda\$fillFromNoise\$4',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator.doFill',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.NoiseChunk.selectCellYZ',
        _minecraft,
      ),
      ('net.minecraft.world.level.levelgen.NoiseChunk.fillSlice', _minecraft),
      (
        'net.minecraft.world.level.levelgen.NoiseChunk\$NoiseInterpolator.fillArray',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.DensityFunctions\$Ap2.fillArray',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.DensityFunctions\$Ap2.compute',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.DensityFunctions\$Mapped.compute',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.DensityFunctions\$RangeChoice.compute',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.DensityFunctions\$Ap2.compute',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.DensityFunctions\$Noise.compute',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.synth.NormalNoise.getValue',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.synth.PerlinNoise.getValue',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.synth.ImprovedNoise.noise',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.synth.ImprovedNoise.sampleAndLerp',
        _minecraft,
      ),
    ], 1.9 * sway);
    worker.path(<(String, String)>[
      ...task,
      (
        'net.minecraft.world.level.chunk.ChunkStatus.lambda\$static\$12',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.chunk.ChunkGenerator.applyBiomeDecoration',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.placement.PlacedFeature.placeWithBiomeCheck',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.placement.PlacedFeature.placeWithContext',
        _minecraft,
      ),
      ('java.util.stream.ReferencePipeline\$3\$1.accept', _java),
      (
        'net.minecraft.world.level.levelgen.placement.PlacedFeature.lambda\$placeWithContext\$2',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.feature.ConfiguredFeature.place',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.feature.TreeFeature.place',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.feature.TreeFeature.doPlace',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer.createFoliage',
        _minecraft,
      ),
      ('net.minecraft.server.level.WorldGenRegion.setBlock', _minecraft),
    ], 0.9 * sway);
    worker.path(<(String, String)>[
      ...task,
      (
        'net.minecraft.world.level.chunk.ChunkStatus.lambda\$static\$14',
        _minecraft,
      ),
      (
        'net.minecraft.server.level.ThreadedLevelLightEngine.lightChunk',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.lighting.LevelLightEngine.runLightUpdates',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.lighting.LightEngine.runLightUpdates',
        _minecraft,
      ),
      (
        'net.minecraft.world.level.lighting.BlockLightEngine.propagateIncrease',
        _minecraft,
      ),
    ], 0.5 * sway);
    if (iris > 0) {
      worker.path(<(String, String)>[
        ...task,
        (
          'net.minecraft.world.level.chunk.ChunkStatus.lambda\$static\$9',
          _minecraft,
        ),
        (
          'net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator.fillFromNoise',
          _minecraft,
        ),
        (
          'art.arcane.iris.engine.platform.BukkitChunkGenerator.generateNoise',
          'Iris',
        ),
        ('art.arcane.iris.engine.IrisEngine.generate', 'Iris'),
        (
          'art.arcane.iris.engine.framework.EngineMantle.generateMatter',
          'Iris',
        ),
        (
          'art.arcane.iris.engine.mantle.components.MantleObjectComponent.generateLayer',
          'Iris',
        ),
        ('art.arcane.iris.engine.object.IrisObject.place', 'Iris'),
        ('art.arcane.iris.util.noise.CNG.noise', 'Iris'),
        ('art.arcane.iris.util.noise.SimplexNoise.noise', 'Iris'),
      ], iris * 0.2 * sway);
    }
    worker.path(<(String, String)>[
      ...pool,
      ('java.util.concurrent.ForkJoinPool.awaitWork', _java),
      ('java.util.concurrent.locks.LockSupport.park', _java),
      ('jdk.internal.misc.Unsafe.park', _java),
    ], 3.2 / sway);
  }

  /// The tree scaled to [samples]; without [idle] the samples parked in
  /// an idle leaf (park, wait, sleep, epoll) are dropped.
  List<FakeFrame> frames(int samples, {bool idle = false}) {
    if (samples <= 0) return const <FakeFrame>[];
    final List<_Shape> shapes = _shapes(samples);
    final List<_Shape> sampled = threads == 'all'
        ? shapes
        : shapes.sublist(0, 1);
    double weight(_Shape shape) => shape.children.fold(
      shape.weight,
      (double sum, _Shape child) => sum + weight(child),
    );
    final double whole = sampled.fold(
      0,
      (double sum, _Shape shape) => sum + weight(shape),
    );
    FakeFrame scale(_Shape shape) => FakeFrame(
      shape.method,
      shape.owner,
      (shape.weight / whole * samples).round(),
      <FakeFrame>[
        for (final _Shape child in shape.children)
          if (weight(child) / whole * samples >= 0.5) scale(child),
      ],
    );
    final List<FakeFrame> scaled = <FakeFrame>[
      for (final _Shape shape in sampled) scale(shape),
    ];
    return idle
        ? scaled
        : <FakeFrame>[for (final FakeFrame root in scaled) ?_busy(root)];
  }

  static final Map<String, String> _names = <String, String>{
    _minecraft: 'Minecraft',
    _java: 'Java',
    for (final FakeOwnerSpec owner in fakeOwnerSpecs) owner.id: owner.name,
  };

  static int _sum(List<FakeFrame> roots) =>
      roots.fold(0, (int sum, FakeFrame root) => sum + root.total);

  static bool _isPlugin(String owner) => owner != _minecraft && owner != _java;

  Map<String, Object?> owners(DateTime now, {bool idle = false}) {
    final List<FakeFrame> roots = frames(samples(now), idle: idle);
    final int total = _sum(roots);
    final Map<String, int> byOwner = <String, int>{};
    final Map<String, Map<String, int>> methods = <String, Map<String, int>>{};
    void walk(FakeFrame frame, String? plugin, String? pluginMethod) {
      final bool own = _isPlugin(frame.owner);
      final String? innermost = own ? frame.owner : plugin;
      final String? method = own ? frame.method : pluginMethod;
      if (frame.self > 0) {
        final String owner = innermost ?? _minecraft;
        byOwner[owner] = (byOwner[owner] ?? 0) + frame.self;
        final String key = method ?? frame.method;
        final Map<String, int> counts = methods.putIfAbsent(
          owner,
          () => <String, int>{},
        );
        counts[key] = (counts[key] ?? 0) + frame.self;
      }
      for (final FakeFrame child in frame.children) {
        walk(child, innermost, method);
      }
    }

    for (final FakeFrame root in roots) {
      walk(root, null, null);
    }
    final List<MapEntry<String, int>> ranked = byOwner.entries.toList()
      ..sort(
        (MapEntry<String, int> a, MapEntry<String, int> b) => b.value - a.value,
      );
    return <String, Object?>{
      'samples': total,
      'owners': <Map<String, Object?>>[
        for (final MapEntry<String, int> entry in ranked)
          <String, Object?>{
            'id': entry.key,
            'name': _names[entry.key] ?? entry.key,
            'samples': entry.value,
            'percent': total == 0 ? 0.0 : entry.value * 100 / total,
            'top': <Map<String, Object?>>[
              for (final MapEntry<String, int> method
                  in (methods[entry.key]!.entries.toList()..sort(
                        (MapEntry<String, int> a, MapEntry<String, int> b) =>
                            b.value - a.value,
                      ))
                      .take(3))
                <String, Object?>{
                  'method': method.key,
                  'samples': method.value,
                },
            ],
          },
      ],
    };
  }

  Map<String, Object?> hot(
    DateTime now, {
    String? owner,
    int limit = 50,
    bool idle = false,
  }) {
    final List<FakeFrame> roots = frames(samples(now), idle: idle);
    final int total = _sum(roots);
    final Map<String, (String, int, int)> byMethod =
        <String, (String, int, int)>{};
    void walk(FakeFrame frame, Set<String> above) {
      if (owner == null || frame.owner == owner) {
        final (String, int, int) seen =
            byMethod[frame.method] ?? (frame.owner, 0, 0);
        byMethod[frame.method] = (
          frame.owner,
          seen.$2 + frame.self,
          seen.$3 + (above.contains(frame.method) ? 0 : frame.total),
        );
      }
      final Set<String> next = <String>{...above, frame.method};
      for (final FakeFrame child in frame.children) {
        walk(child, next);
      }
    }

    for (final FakeFrame root in roots) {
      for (final FakeFrame child in root.children) {
        walk(child, <String>{});
      }
    }
    final List<MapEntry<String, (String, int, int)>> ranked =
        byMethod.entries.toList()..sort((
          MapEntry<String, (String, int, int)> a,
          MapEntry<String, (String, int, int)> b,
        ) {
          final int bySelf = b.value.$2 - a.value.$2;
          return bySelf != 0 ? bySelf : b.value.$3 - a.value.$3;
        });
    return <String, Object?>{
      'samples': total,
      'methods': <Map<String, Object?>>[
        for (final MapEntry<String, (String, int, int)> entry in ranked.take(
          limit.clamp(1, 500),
        ))
          <String, Object?>{
            'method': entry.key,
            'owner': entry.value.$1,
            'ownerName': _names[entry.value.$1] ?? entry.value.$1,
            'self': entry.value.$2,
            'total': entry.value.$3,
            'selfPercent': total == 0 ? 0.0 : entry.value.$2 * 100 / total,
            'totalPercent': total == 0 ? 0.0 : entry.value.$3 * 100 / total,
          },
      ],
    };
  }

  /// [frame] kept where it holds [owner]'s frames: an owned frame whole,
  /// an ancestor with only the children that lead to one.
  static FakeFrame? _prune(FakeFrame frame, String owner) {
    if (frame.owner == owner) return frame;
    final List<FakeFrame> kept = <FakeFrame>[
      for (final FakeFrame child in frame.children) ?_prune(child, owner),
    ];
    return kept.isEmpty ? null : FakeFrame(frame.method, frame.owner, 0, kept);
  }

  Map<String, Object?> tree(
    DateTime now, {
    String? owner,
    String? thread,
    double minPercent = 0.5,
    int maxDepth = 64,
    bool group = true,
    bool collapse = true,
    bool idle = false,
  }) {
    final List<FakeFrame> roots = frames(samples(now), idle: idle);
    final int total = _sum(roots);
    final double floor = total * minPercent.clamp(0, 100) / 100;
    Map<String, Object?> encode(FakeFrame frame, int depth) {
      final List<FakeFrame> shown = <FakeFrame>[];
      int folded = 0;
      for (final FakeFrame child in frame.children) {
        if (child.total < floor) {
          folded += child.total;
        } else {
          shown.add(child);
        }
      }
      shown.sort((FakeFrame a, FakeFrame b) => b.total - a.total);
      final bool deeper = depth + 1 < maxDepth.clamp(1, 256);
      return <String, Object?>{
        'method': frame.method,
        'owner': frame.owner,
        'ownerName': _names[frame.owner] ?? frame.owner,
        'total': frame.total,
        'self': frame.self,
        'children': !deeper
            ? const <Map<String, Object?>>[]
            : <Map<String, Object?>>[
                for (final FakeFrame child in shown) encode(child, depth + 1),
                if (folded > 0)
                  <String, Object?>{
                    'method': '(other)',
                    'owner': '',
                    'ownerName': '',
                    'total': folded,
                    'self': folded,
                    'children': const <Map<String, Object?>>[],
                  },
              ],
      };
    }

    final List<FakeFrame> grouped = group ? groupThreads(roots) : roots;
    List<FakeFrame> named = <FakeFrame>[
      for (final FakeFrame root in grouped)
        if (thread == null || root.method == thread) root,
    ];
    if (named.isEmpty && thread != null) {
      named = <FakeFrame>[
        for (final FakeFrame root in roots)
          if (root.method == thread) root,
      ];
    }
    final List<FakeFrame> kept = <FakeFrame>[
      for (final FakeFrame root in named)
        if (owner == null) root else ?_prune(root, owner),
    ]..sort((FakeFrame a, FakeFrame b) => b.total - a.total);
    return <String, Object?>{
      'samples': total,
      'roots': <Map<String, Object?>>[
        for (final FakeFrame root in kept)
          if (collapse) collapsePlumbing(encode(root, 0)) else encode(root, 0),
      ],
    };
  }

  static const List<String> _idleLeaves = <String>[
    'java.util.concurrent.locks.LockSupport.park',
    'jdk.internal.misc.Unsafe.park',
    'sun.misc.Unsafe.park',
    'java.lang.Object.wait',
    'java.lang.Thread.sleep',
    'java.util.concurrent.ForkJoinPool.awaitWork',
    'io.netty.channel.epoll.Native.epollWait',
  ];

  /// Whether a leaf in [method] is a thread waiting for work.
  static bool isIdleFrame(String method) =>
      _idleLeaves.any((String idle) => method.startsWith(idle));

  /// [frame] without the samples parked in an idle frame; null when
  /// nothing else is left.
  static FakeFrame? _busy(FakeFrame frame) {
    final List<FakeFrame> children = <FakeFrame>[
      for (final FakeFrame child in frame.children) ?_busy(child),
    ];
    final int self = isIdleFrame(frame.method) ? 0 : frame.self;
    if (self == 0 && children.isEmpty) return null;
    return FakeFrame(frame.method, frame.owner, self, children);
  }

  static final RegExp _numbered = RegExp(r'^(.*?)[\s#_-]*\d+$');

  /// The name [thread] shares with its numbered siblings, or null.
  static String? threadGroupOf(String thread) {
    final RegExpMatch? match = _numbered.firstMatch(thread);
    final String base = match?.group(1)?.trim() ?? '';
    return base.isEmpty ? null : base;
  }

  /// [roots] with numbered threads of one name merged into one root,
  /// "Worker-Main (12)", their frames summed by method.
  static List<FakeFrame> groupThreads(List<FakeFrame> roots) {
    final Map<String, List<FakeFrame>> byGroup = <String, List<FakeFrame>>{};
    for (final FakeFrame root in roots) {
      final String key = threadGroupOf(root.method) ?? root.method;
      byGroup.putIfAbsent(key, () => <FakeFrame>[]).add(root);
    }
    return <FakeFrame>[
      for (final MapEntry<String, List<FakeFrame>> entry in byGroup.entries)
        if (entry.value.length < 2)
          entry.value.single
        else
          _merge('${entry.key} (${entry.value.length})', entry.value),
    ];
  }

  static FakeFrame _merge(String method, List<FakeFrame> frames) {
    final Map<String, List<FakeFrame>> byMethod = <String, List<FakeFrame>>{};
    int self = 0;
    for (final FakeFrame frame in frames) {
      self += frame.self;
      for (final FakeFrame child in frame.children) {
        byMethod.putIfAbsent(child.method, () => <FakeFrame>[]).add(child);
      }
    }
    return FakeFrame(method, frames.first.owner, self, <FakeFrame>[
      for (final MapEntry<String, List<FakeFrame>> entry in byMethod.entries)
        entry.value.length == 1
            ? entry.value.single
            : _merge(entry.key, entry.value),
    ]);
  }

  static const List<String> _plumbingClasses = <String>[
    'ForkJoin',
    'CompletableFuture',
    'ProcessorMailbox',
  ];

  /// Whether [node] is JDK or scheduler glue a chain of can fold into one
  /// row: a JDK frame, a ForkJoin, CompletableFuture or ProcessorMailbox
  /// frame, or a lambda; never a plugin's own frame.
  static bool isPlumbing(Map<String, Object?> node) {
    final String owner = node['owner'] as String? ?? '';
    if (owner != _java && owner != _minecraft) return false;
    final String method = node['method']! as String;
    if (method.startsWith('(')) return false;
    if (method.startsWith('java.') ||
        method.startsWith('jdk.') ||
        method.startsWith('sun.') ||
        method.contains(r'lambda$')) {
      return true;
    }
    final int dot = method.lastIndexOf('.');
    final String type = dot < 0 ? method : method.substring(0, dot);
    final String simple = type.substring(type.lastIndexOf('.') + 1);
    return _plumbingClasses.any(simple.startsWith);
  }

  static List<Map<String, Object?>> _childrenOf(Map<String, Object?> node) =>
      (node['children'] as List<Object?>? ?? const <Object?>[])
          .cast<Map<String, Object?>>();

  /// [node] with every chain of two or more plumbing frames, each with one
  /// child, folded into one "(N frames)" node that lists them top-down.
  static Map<String, Object?> collapsePlumbing(Map<String, Object?> node) =>
      <String, Object?>{
        ...node,
        'children': <Map<String, Object?>>[
          for (final Map<String, Object?> child in _childrenOf(node))
            _collapseChain(child),
        ],
      };

  static Map<String, Object?> _collapseChain(Map<String, Object?> start) {
    final List<Map<String, Object?>> chain = <Map<String, Object?>>[];
    Map<String, Object?> node = start;
    while (isPlumbing(node) && _childrenOf(node).length == 1) {
      chain.add(node);
      node = _childrenOf(node).single;
    }
    if (chain.length < 2) return collapsePlumbing(start);
    final List<Map<String, Object?>> rest = _childrenOf(chain.last);
    return <String, Object?>{
      'method': '(${chain.length} frames)',
      'owner': _java,
      'ownerName': _names[_java],
      'total': chain.first['total'],
      'self': chain.fold<int>(
        0,
        (int sum, Map<String, Object?> frame) => sum + (frame['self']! as int),
      ),
      'collapsed': <String>[
        for (final Map<String, Object?> frame in chain)
          frame['method']! as String,
      ],
      'children': <Map<String, Object?>>[
        for (final Map<String, Object?> child in rest) _collapseChain(child),
      ],
    };
  }

  Map<String, Object?> threadList(DateTime now, {bool idle = false}) {
    final List<FakeFrame> roots = frames(samples(now), idle: idle);
    final int total = _sum(roots);
    final Map<String, String> states = <String, String>{
      for (final (String name, String state) in _threadNames) name: state,
    };
    roots.sort((FakeFrame a, FakeFrame b) => b.total - a.total);
    return <String, Object?>{
      'threads': <Map<String, Object?>>[
        for (final FakeFrame root in roots)
          <String, Object?>{
            'name': root.method,
            'state': states[root.method] ?? 'RUNNABLE',
            'samples': root.total,
            'percent': total == 0 ? 0.0 : root.total * 100 / total,
          },
      ],
    };
  }

  /// The profile a snapshot freezes: the live routes' shapes under one
  /// object.
  Map<String, Object?> freeze(DateTime now, {bool idle = false}) =>
      <String, Object?>{
        'profiler': status(now),
        'owners': owners(now, idle: idle),
        'hot': hot(now, idle: idle),
        'tree': tree(now, minPercent: 0.1, idle: idle),
        'threads': threadList(now, idle: idle),
      };
}

/// A frozen profile or heatmap.
final class FakeSnapshot {
  FakeSnapshot(this.meta, this.data);

  final Map<String, Object?> meta;
  final Map<String, Object?> data;

  Map<String, Object?> get detail => <String, Object?>{
    'meta': meta,
    'data': data,
  };

  String get id => meta['id']! as String;
  String get kind => meta['kind']! as String;

  /// The export route's body: the detail as gzip JSON.
  List<int> get gzip => GZipCodec().encode(utf8.encode(jsonEncode(detail)));

  /// `react-<kind>-<server>-<yyyyMMdd-HHmmss>.json.gz`.
  String get filename {
    final DateTime at = DateTime.fromMillisecondsSinceEpoch(
      meta['createdAt']! as int,
    );
    String two(int value) => value.toString().padLeft(2, '0');
    final String server = (meta['server']! as String).replaceAll(
      RegExp(r'[^A-Za-z0-9._-]+'),
      '-',
    );
    return 'react-$kind-$server-${at.year}${two(at.month)}${two(at.day)}-'
        '${two(at.hour)}${two(at.minute)}${two(at.second)}.json.gz';
  }
}

final class FakeSnapshots {
  FakeSnapshots(this.profile);

  final FakeServerProfile profile;
  final List<FakeSnapshot> _all = <FakeSnapshot>[];
  int _count = 0;

  /// The newest kept, as the plugin's `snapshots.keep`.
  static const int keep = 50;

  List<Map<String, Object?>> list(String? kind) => <Map<String, Object?>>[
    for (final FakeSnapshot snapshot in _all.reversed)
      if (kind == null || kind.isEmpty || snapshot.kind == kind) snapshot.meta,
  ];

  FakeSnapshot? byId(String id) {
    for (final FakeSnapshot snapshot in _all) {
      if (snapshot.id == id) return snapshot;
    }
    return null;
  }

  bool remove(String id) {
    final int before = _all.length;
    _all.removeWhere((FakeSnapshot snapshot) => snapshot.id == id);
    return _all.length != before;
  }

  FakeSnapshot add(
    String kind,
    String? label,
    String summary,
    Map<String, Object?> data,
    DateTime now,
  ) {
    String two(int value) => value.toString().padLeft(2, '0');
    final String stamp =
        '${now.year}-${two(now.month)}-${two(now.day)} '
        '${two(now.hour)}:${two(now.minute)}:${two(now.second)}';
    final String id =
        'snap-${profile.slug}-${++_count}-${now.millisecondsSinceEpoch.toRadixString(36)}';
    final Map<String, Object?> meta = <String, Object?>{
      'id': id,
      'kind': kind,
      'label': label == null || label.trim().isEmpty
          ? '${kind == 'profile' ? 'Profile' : 'Heatmap'} $stamp'
          : label.trim(),
      'createdAt': now.millisecondsSinceEpoch,
      'server': profile.label,
      'bytes': 0,
      'summary': summary,
    };
    final FakeSnapshot snapshot = FakeSnapshot(meta, data);
    meta['bytes'] = snapshot.gzip.length;
    _all.add(snapshot);
    if (_all.length > keep) _all.removeRange(0, _all.length - keep);
    return snapshot;
  }
}

/// "1,234".
String fakeCount(int value) => value.toString().replaceAllMapped(
  RegExp(r'\B(?=(\d{3})+(?!\d))'),
  (Match match) => ',',
);
