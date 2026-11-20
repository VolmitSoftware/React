package art.arcane.react.platform.cost;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class OwnerCostModelTest {
  private static final long MS_TO_NANOS = 1_000_000L;

  @Test
  void defaultModeIsOnDemandAndStaysInactiveWithoutDemand() {
    Fixture fixture = new Fixture();
    fixture.model.start();

    Assertions.assertEquals(OwnerCostMode.ON_DEMAND, fixture.model.mode());
    Assertions.assertFalse(fixture.model.tick());
    Assertions.assertFalse(fixture.probe.active());
    Assertions.assertFalse(fixture.model.measuring());
  }

  @Test
  void nullModeFallsBackToOnDemand() {
    Fixture fixture = new Fixture();
    fixture.model.mode(null);

    Assertions.assertEquals(OwnerCostMode.ON_DEMAND, fixture.model.mode());
  }

  @Test
  void alwaysModeActivatesWithoutDemand() {
    Fixture fixture = new Fixture();
    fixture.model.mode(OwnerCostMode.ALWAYS);
    fixture.model.start();

    Assertions.assertTrue(fixture.model.tick());
    Assertions.assertTrue(fixture.probe.active());
    fixture.clock.advance(60_000L);
    Assertions.assertTrue(fixture.model.tick());
  }

  @Test
  void markActivityActivatesOnDemandImmediately() {
    Fixture fixture = new Fixture();
    fixture.model.start();

    fixture.model.markActivity();

    Assertions.assertTrue(fixture.probe.active());
    Assertions.assertEquals(List.of(true), fixture.probe.activations);
  }

  @Test
  void markActivityIsIgnoredBeforeStart() {
    Fixture fixture = new Fixture();

    fixture.model.markActivity();

    Assertions.assertFalse(fixture.probe.active());
  }

  @Test
  void onDemandReleasesFifteenSecondsAfterTheLastRead() {
    Fixture fixture = new Fixture();
    fixture.model.start();
    fixture.model.markActivity();

    fixture.clock.advance(15_000L);
    Assertions.assertTrue(fixture.model.tick());
    Assertions.assertTrue(fixture.probe.active());

    fixture.clock.advance(1L);
    Assertions.assertFalse(fixture.model.tick());
    Assertions.assertFalse(fixture.probe.active());
  }

  @Test
  void dutyCycleOpensFiveSecondsEveryMinute() {
    Fixture fixture = new Fixture();
    fixture.model.mode(OwnerCostMode.DUTY_CYCLE);
    fixture.model.start();

    Assertions.assertTrue(fixture.model.tick());
    fixture.clock.advance(4_999L);
    Assertions.assertTrue(fixture.model.tick());
    fixture.clock.advance(1L);
    Assertions.assertFalse(fixture.model.tick());
    fixture.clock.advance(54_999L);
    Assertions.assertFalse(fixture.model.tick());
    fixture.clock.advance(1L);
    Assertions.assertTrue(fixture.model.tick());
  }

  @Test
  void dutyCycleStaysOpenWhileDemanded() {
    Fixture fixture = new Fixture();
    fixture.model.mode(OwnerCostMode.DUTY_CYCLE);
    fixture.model.start();
    fixture.model.tick();

    fixture.clock.advance(10_000L);
    fixture.model.markActivity();

    Assertions.assertTrue(fixture.model.tick());
  }

  @Test
  void tickPublishesDrainedOwnersAsMillisecondsPerSecond() {
    Fixture fixture = new Fixture();
    fixture.model.mode(OwnerCostMode.ALWAYS);
    fixture.model.start();
    fixture.model.tick();

    fixture.probe.record("alpha", "Alpha", 20 * MS_TO_NANOS, 4L, 1L);
    fixture.probe.record("beta", "Beta", 5 * MS_TO_NANOS, 2L, 0L);
    fixture.clock.advance(2_000L);
    fixture.model.tick();

    Assertions.assertTrue(fixture.model.measuring());
    Assertions.assertEquals(2D, fixture.model.windowSeconds(), 1.0E-9D);
    Assertions.assertEquals(10D, fixture.model.msPerSecond("alpha"), 1.0E-9D);
    Assertions.assertEquals(20D, fixture.model.ms("alpha"), 1.0E-9D);
    Assertions.assertEquals(2.5D, fixture.model.msPerSecond("beta"), 1.0E-9D);
    Assertions.assertEquals(12.5D, fixture.model.totalMsPerSecond(), 1.0E-9D);
    Assertions.assertEquals(6L, fixture.model.calls());
    Assertions.assertEquals(1L, fixture.model.asyncCalls());
    Assertions.assertEquals(Map.of("alpha", 4, "beta", 2), fixture.model.snapshotCalls());
    Assertions.assertEquals("Alpha", fixture.model.displayNames().get("alpha"));
    Assertions.assertEquals(0D, fixture.model.msPerSecond("missing"));
  }

  @Test
  void emptyDrainPublishesAnIdleWindow() {
    Fixture fixture = new Fixture();
    fixture.model.mode(OwnerCostMode.ALWAYS);
    fixture.model.start();
    fixture.model.tick();
    fixture.probe.record("alpha", "Alpha", 10 * MS_TO_NANOS, 1L, 0L);
    fixture.clock.advance(1_000L);
    fixture.model.tick();

    fixture.clock.advance(1_000L);
    fixture.model.collect();

    Assertions.assertTrue(fixture.model.measuring());
    Assertions.assertEquals(0D, fixture.model.msPerSecond("alpha"));
    Assertions.assertEquals(0L, fixture.model.calls());
  }

  @Test
  void applyModeSwitchesTheProbeWithoutDraining() {
    Fixture fixture = new Fixture();
    fixture.model.mode(OwnerCostMode.ALWAYS);
    fixture.model.start();
    fixture.probe.record("alpha", "Alpha", 10 * MS_TO_NANOS, 1L, 0L);

    Assertions.assertTrue(fixture.model.applyMode());

    Assertions.assertTrue(fixture.probe.active());
    Assertions.assertFalse(fixture.model.measuring());
  }

  @Test
  void deactivationPublishesTheFinalDrain() {
    Fixture fixture = new Fixture();
    fixture.model.start();
    fixture.model.markActivity();
    fixture.probe.record("alpha", "Alpha", 30 * MS_TO_NANOS, 3L, 0L);

    fixture.clock.advance(15_001L);
    Assertions.assertFalse(fixture.model.tick());

    Assertions.assertEquals(3, fixture.model.snapshotCalls().get("alpha"));
    Assertions.assertTrue(fixture.model.measuring());
  }

  @Test
  void windowsGoStaleAfterTheFreshnessLimitAndStopClearsThem() {
    Fixture fixture = new Fixture();
    fixture.model.mode(OwnerCostMode.ALWAYS);
    fixture.model.freshnessMs(3_000L);
    fixture.model.start();
    fixture.model.tick();
    fixture.probe.record("alpha", "Alpha", 10 * MS_TO_NANOS, 1L, 0L);
    fixture.clock.advance(1_000L);
    fixture.model.tick();

    fixture.clock.advance(3_001L);
    Assertions.assertFalse(fixture.model.measuring());
    Assertions.assertEquals(0D, fixture.model.msPerSecond("alpha"));
    Assertions.assertTrue(fixture.model.snapshotMs().isEmpty());

    fixture.model.stop();
    Assertions.assertFalse(fixture.model.running());
    Assertions.assertFalse(fixture.model.tick());
    Assertions.assertFalse(fixture.probe.active());
  }

  @Test
  void sampleAverageSpansFiveSamplesAndSeedsFromTheFirst() {
    Fixture fixture = new Fixture();
    fixture.model.mode(OwnerCostMode.ALWAYS);
    fixture.model.start();
    fixture.model.tick();

    fixture.publish("alpha", 10L);
    Assertions.assertEquals(10D, fixture.model.sampleAverage("alpha"), 1.0E-9D);

    fixture.publish("alpha", 20L);
    Assertions.assertEquals(12D, fixture.model.sampleAverage("alpha"), 1.0E-9D);

    for (int index = 0; index < 4; index++) {
      fixture.publish("alpha", 20L);
      fixture.model.sampleAverage("alpha");
    }
    Assertions.assertEquals(20D, fixture.model.sampleAverage("alpha"), 1.0E-9D);

    fixture.model.forgetOwner("alpha");
    fixture.publish("alpha", 5L);
    Assertions.assertEquals(5D, fixture.model.sampleAverage("alpha"), 1.0E-9D);
  }

  @Test
  void kindsAreSummedPerOwnerAndAcrossOwners() {
    Fixture fixture = new Fixture();
    fixture.model.mode(OwnerCostMode.ALWAYS);
    fixture.model.start();
    fixture.model.tick();
    fixture.probe.record("alpha", "Alpha", "event", 4 * MS_TO_NANOS, 2L, 0L);
    fixture.probe.record("alpha", "Alpha", "entity", 6 * MS_TO_NANOS, 3L, 0L);
    fixture.probe.record("beta", "Beta", "Block_Entity", 5 * MS_TO_NANOS, 1L, 0L);
    fixture.probe.record("beta", "Beta", null, 1 * MS_TO_NANOS, 1L, 0L);
    fixture.clock.advance(1_000L);
    fixture.model.collect();

    Assertions.assertEquals(10D, fixture.model.snapshotMs().get("alpha"), 1.0E-9D);
    Assertions.assertEquals(5, fixture.model.snapshotCalls().get("alpha"));
    Assertions.assertEquals(Map.of("event", 4D, "entity", 6D), fixture.model.snapshotOwnerKindMs().get("alpha"));
    Assertions.assertEquals(Map.of("block_entity", 5D, "event", 1D), fixture.model.snapshotOwnerKindMs().get("beta"));
    Assertions.assertEquals(Map.of("event", 5D, "entity", 6D, "block_entity", 5D), fixture.model.snapshotKindMs());
  }

  @Test
  void recentCostSmoothsWindowsAndHoldsWhileIdle() {
    Fixture fixture = new Fixture();
    fixture.model.mode(OwnerCostMode.ALWAYS);
    fixture.model.start();
    fixture.model.tick();

    fixture.publish("alpha", 10L);
    Assertions.assertEquals(10D, fixture.model.recentMsPerSecond("alpha"), 1.0E-9D);
    fixture.publish("alpha", 20L);
    Assertions.assertEquals(13D, fixture.model.recentMsPerSecond("alpha"), 1.0E-9D);
    fixture.publish("beta", 1L);
    Assertions.assertEquals(9.1D, fixture.model.recentMsPerSecond("alpha"), 1.0E-9D);

    fixture.clock.advance(60_000L);
    Assertions.assertFalse(fixture.model.measuring());
    Assertions.assertEquals(9.1D, fixture.model.recentMsPerSecond("alpha"), 1.0E-9D);
    Assertions.assertEquals(Map.of("alpha", 9.1D, "beta", 1D).keySet(), fixture.model.recentSnapshot().keySet());

    fixture.model.forgetOwner("beta");
    Assertions.assertEquals(0D, fixture.model.recentMsPerSecond("beta"));
  }

  @Test
  void unavailableProbeIsNeverActivated() {
    Fixture fixture = new Fixture();
    fixture.probe.available = false;
    fixture.model.mode(OwnerCostMode.ALWAYS);
    fixture.model.start();

    Assertions.assertFalse(fixture.model.tick());
    fixture.model.markActivity();
    Assertions.assertTrue(fixture.probe.activations.isEmpty());
  }

  @Test
  void samplerIdsUseTheOwnerNoun() {
    Assertions.assertEquals("plugin-my-plugin", OwnerCostModel.samplerId("plugin", " My Plugin "));
    Assertions.assertEquals("mod-create", OwnerCostModel.samplerId("mod", "create"));
    Assertions.assertEquals("plugin-", OwnerCostModel.samplerId("plugin", null));
  }

  private static final class Fixture {
    private final ManualClock clock = new ManualClock();
    private final RecordingProbe probe = new RecordingProbe();
    private final OwnerCostModel model = new OwnerCostModel(probe, clock::millis, clock::nanos);

    private void publish(String owner, long msPerSecond) {
      probe.record(owner, owner, msPerSecond * MS_TO_NANOS, 1L, 0L);
      clock.advance(1_000L);
      model.collect();
    }
  }

  private static final class ManualClock {
    private long millis = 1_000_000L;

    private long millis() {
      return millis;
    }

    private long nanos() {
      return millis * MS_TO_NANOS;
    }

    private void advance(long deltaMs) {
      millis += deltaMs;
    }
  }

  private static final class RecordingProbe implements OwnerCostProbe {
    private final List<Boolean> activations = new ArrayList<>();
    private final List<Object[]> pending = new ArrayList<>();
    private boolean available = true;
    private boolean active;

    private void record(String owner, String display, long nanos, long sync, long async) {
      record(owner, display, null, nanos, sync, async);
    }

    private void record(String owner, String display, String kind, long nanos, long sync, long async) {
      pending.add(new Object[]{owner, display, kind, nanos, sync, async});
    }

    @Override
    public boolean available() {
      return available;
    }

    @Override
    public String unavailableReason() {
      return available ? "" : "test";
    }

    @Override
    public void setActive(boolean active) {
      activations.add(active);
      this.active = active;
    }

    @Override
    public boolean active() {
      return active;
    }

    @Override
    public void drain(OwnerCostConsumer consumer) {
      for (Object[] entry : pending) {
        consumer.accept((String) entry[0], (String) entry[1], (String) entry[2], (Long) entry[3], (Long) entry[4], (Long) entry[5]);
      }
      pending.clear();
    }
  }
}
