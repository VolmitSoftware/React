package art.arcane.react.content.feature;

import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.PlatformKind;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.PortalHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.RedstoneHost;
import art.arcane.react.platform.SpawnHost;
import art.arcane.react.platform.Subscription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

class RedstoneGuardrailsTest {
  private static final UUID WORLD = UUID.randomUUID();

  private final List<Consumer<RedstoneHost.Signal>> signals = new ArrayList<>();
  private final List<ListenerPriority> signalPriorities = new ArrayList<>();
  private final List<Consumer<SpawnHost.Spawn>> spawns = new ArrayList<>();
  private final List<Consumer<PortalHost.Portal>> portals = new ArrayList<>();

  @BeforeEach
  void bind() {
    ReactPlatforms.bind(new TestPlatform());
  }

  @AfterEach
  void unbind() {
    ReactPlatforms.unbind();
  }

  @Test
  void clockGovernorHoldsABlockAtItsOldPowerOnceItTogglesTooOften() throws Exception {
    FeatureRedstoneClockGovernor governor = new FeatureRedstoneClockGovernor();
    set(governor, "maxTransitionsPerWindow", 3);
    set(governor, "windowMS", 60000);
    governor.onActivate();
    Assertions.assertEquals(List.of(ListenerPriority.LOWEST), signalPriorities);

    List<TestSignal> fired = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      TestSignal signal = new TestSignal(new GameLocation(WORLD, 4, 64, 4), i % 2 == 0 ? 0 : 15, i % 2 == 0 ? 15 : 0);
      dispatch(signal);
      fired.add(signal);
    }
    TestSignal otherBlock = new TestSignal(new GameLocation(WORLD, 40, 64, 4), 0, 15);
    dispatch(otherBlock);

    for (int i = 0; i < 3; i++) {
      Assertions.assertNotEquals(fired.get(i).oldCurrent(), fired.get(i).newCurrent(), "transition " + i + " must pass");
    }
    for (int i = 3; i < 6; i++) {
      Assertions.assertEquals(fired.get(i).oldCurrent(), fired.get(i).newCurrent(), "transition " + i + " must be held");
    }
    Assertions.assertEquals(15, otherBlock.newCurrent());

    governor.onDeactivate();
    Assertions.assertTrue(signals.isEmpty());
  }

  @Test
  void clockGovernorIgnoresSignalsThatDoNotChangePower() throws Exception {
    FeatureRedstoneClockGovernor governor = new FeatureRedstoneClockGovernor();
    set(governor, "maxTransitionsPerWindow", 0);
    governor.onActivate();
    TestSignal steady = new TestSignal(new GameLocation(WORLD, 0, 64, 0), 7, 7);
    TestSignal change = new TestSignal(new GameLocation(WORLD, 0, 64, 0), 7, 8);
    dispatch(steady);
    dispatch(change);
    governor.onDeactivate();

    Assertions.assertEquals(7, steady.newCurrent());
    Assertions.assertEquals(7, change.newCurrent());
  }

  @Test
  void incidentModeLimitsSpawnersNaturalSpawnsAndPortalsOnlyDuringAnIncident() throws Exception {
    FeatureIncidentMode incident = new FeatureIncidentMode();
    set(incident, "bypassNearPlayers", false);
    set(incident, "maxSpawnerSpawnsPerWindow", 1);
    set(incident, "maxNaturalSpawnsPerWindow", 1);
    set(incident, "maxPortalEventsPerWindow", 1);
    set(incident, "rateWindowMS", 60000);
    incident.onActivate();

    TestSpawn beforeIncident = spawn("SPAWNER");
    TestSpawn beforeIncidentAgain = spawn("SPAWNER");
    set(incident, "incident", true);
    TestSpawn trial = spawn("TRIAL_SPAWNER");
    TestSpawn spawner = spawn("SPAWNER");
    TestSpawn natural = spawn("NATURAL");
    TestSpawn raid = spawn("RAID");
    TestSpawn egg = spawn("SPAWNER_EGG");
    TestPortal firstPortal = portal();
    TestPortal secondPortal = portal();
    incident.onDeactivate();

    Assertions.assertFalse(beforeIncident.cancelled);
    Assertions.assertFalse(beforeIncidentAgain.cancelled);
    Assertions.assertFalse(trial.cancelled);
    Assertions.assertTrue(spawner.cancelled);
    Assertions.assertFalse(natural.cancelled);
    Assertions.assertTrue(raid.cancelled);
    Assertions.assertFalse(egg.cancelled);
    Assertions.assertFalse(firstPortal.cancelled);
    Assertions.assertTrue(secondPortal.cancelled);
    Assertions.assertTrue(spawns.isEmpty());
    Assertions.assertTrue(portals.isEmpty());
  }

  @Test
  void circuitManagerHoldsSignalsOfAThrottledComponent() throws Exception {
    FeatureCircuitManager manager = new FeatureCircuitManager();
    manager.onActivate();
    Assertions.assertEquals(List.of(ListenerPriority.HIGHEST), signalPriorities);
    Field serverField = FeatureCircuitManager.class.getDeclaredField("circuitServer");
    serverField.setAccessible(true);
    art.arcane.react.model.CircuitServer server = (art.arcane.react.model.CircuitServer) serverField.get(manager);

    TestSignal first = new TestSignal(new GameLocation(WORLD, 0, 64, 0), 0, 15);
    dispatch(first);
    server.rollWindow(System.currentTimeMillis(), 15000L);
    Assertions.assertNotNull(server.throttleWorst(System.currentTimeMillis(), 60000L));
    TestSignal blocked = new TestSignal(new GameLocation(WORLD, 1, 64, 0), 0, 15);
    dispatch(blocked);
    manager.onDeactivate();

    Assertions.assertEquals(15, first.newCurrent());
    Assertions.assertEquals(0, blocked.newCurrent());
  }

  private void dispatch(TestSignal signal) {
    for (Consumer<RedstoneHost.Signal> listener : List.copyOf(signals)) {
      listener.accept(signal);
    }
  }

  private TestSpawn spawn(String reason) {
    TestSpawn spawn = new TestSpawn(reason);
    for (Consumer<SpawnHost.Spawn> listener : List.copyOf(spawns)) {
      listener.accept(spawn);
    }
    return spawn;
  }

  private TestPortal portal() {
    TestPortal portal = new TestPortal();
    for (Consumer<PortalHost.Portal> listener : List.copyOf(portals)) {
      listener.accept(portal);
    }
    return portal;
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static <T> Subscription add(List<T> listeners, T listener) {
    listeners.add(listener);
    return () -> listeners.remove(listener);
  }

  private static final class TestSignal implements RedstoneHost.Signal {
    private final GameLocation location;
    private final int oldCurrent;
    private int newCurrent;

    private TestSignal(GameLocation location, int oldCurrent, int newCurrent) {
      this.location = location;
      this.oldCurrent = oldCurrent;
      this.newCurrent = newCurrent;
    }

    @Override
    public GameLocation location() {
      return location;
    }

    @Override
    public String type() {
      return "REPEATER";
    }

    @Override
    public int oldCurrent() {
      return oldCurrent;
    }

    @Override
    public int newCurrent() {
      return newCurrent;
    }

    @Override
    public void setNewCurrent(int current) {
      newCurrent = current;
    }
  }

  private static final class TestSpawn implements SpawnHost.Spawn {
    private final String reason;
    private boolean cancelled;

    private TestSpawn(String reason) {
      this.reason = reason;
    }

    @Override
    public GameEntity entity() {
      return Mockito.mock(GameEntity.class);
    }

    @Override
    public GameLocation location() {
      return new GameLocation(WORLD, 0, 64, 0);
    }

    @Override
    public String type() {
      return "ZOMBIE";
    }

    @Override
    public String reason() {
      return reason;
    }

    @Override
    public boolean isMonster() {
      return true;
    }

    @Override
    public boolean hasCustomName() {
      return false;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }
  }

  private static final class TestPortal implements PortalHost.Portal {
    private boolean cancelled;

    @Override
    public GameEntity entity() {
      return Mockito.mock(GameEntity.class);
    }

    @Override
    public boolean isPlayer() {
      return true;
    }

    @Override
    public GameLocation from() {
      return new GameLocation(WORLD, 0, 64, 0);
    }

    @Override
    public GameLocation to() {
      return new GameLocation(WORLD, 0, 64, 0);
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }
  }

  private final class TestPlatform implements ReactPlatform {
    @Override
    public PlatformKind kind() {
      return PlatformKind.MODDED;
    }

    @Override
    public String platformName() {
      return "NeoForge";
    }

    @Override
    public String minecraftVersion() {
      return "26.3";
    }

    @Override
    public File dataFolder() {
      return null;
    }

    @Override
    public PlatformScheduler scheduler() {
      return Mockito.mock(PlatformScheduler.class);
    }

    @Override
    public ExecutorService asyncExecutor() {
      return Mockito.mock(ExecutorService.class);
    }

    @Override
    public void info(String message) {
    }

    @Override
    public void warn(String message) {
    }

    @Override
    public void verbose(String message) {
    }

    @Override
    public void reportError(String context, Throwable failure) {
    }

    @Override
    public Optional<RedstoneHost> redstoneHost() {
      return Optional.of((priority, listener) -> {
        signalPriorities.add(priority);
        return add(signals, listener);
      });
    }

    @Override
    public Optional<SpawnHost> spawnHost() {
      SpawnHost host = Mockito.mock(SpawnHost.class);
      Mockito.when(host.onCreatureSpawn(Mockito.any(), Mockito.anyBoolean(), Mockito.any()))
          .thenAnswer(invocation -> add(spawns, invocation.getArgument(2)));
      return Optional.of(host);
    }

    @Override
    public Optional<PortalHost> portalHost() {
      return Optional.of((priority, ignoreCancelled, listener) -> add(portals, listener));
    }
  }
}
