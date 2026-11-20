package art.arcane.react.content.feature;

import art.arcane.react.core.controller.LoadedChunkCoordinate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class TickSpikeReplayTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Test
  void claimsOnlySpikesOutsideTheCooldown() {
    TickSpikeReplay replay = new TickSpikeReplay(TickSpikeReplay.DEFAULT_SETTINGS);
    replay.reset(1_000L);
    Assertions.assertFalse(replay.claimCapture(2_000L, 49D), "below the threshold");
    Assertions.assertTrue(replay.claimCapture(2_000L, 80D));
    Assertions.assertFalse(replay.claimCapture(2_200L, 80D), "inside the cooldown");
    Assertions.assertTrue(replay.claimCapture(2_400L, 80D));
  }

  @Test
  void scoredCaptureHeatsTheOriginMostAndWeighsChunkCost() {
    TickSpikeReplay replay = new TickSpikeReplay(TickSpikeReplay.DEFAULT_SETTINGS);
    List<LoadedChunkCoordinate> loaded = List.of(
        new LoadedChunkCoordinate(0, 0), new LoadedChunkCoordinate(1, 0), new LoadedChunkCoordinate(-1, 0));
    replay.captureScored(WORLD, 0, 0, loaded, (int x, int z) -> x > 0 ? 240D : 0D, 70D, 1_000L);

    double origin = replay.heat(WORLD, 0, 0);
    Assertions.assertEquals(21D + (21D * 1.8D), origin, 1.0E-9D);
    Assertions.assertTrue(replay.heat(WORLD, 1, 0) > replay.heat(WORLD, -1, 0));
    Assertions.assertEquals(0D, replay.heat(WORLD, 5, 5));
  }

  @Test
  void decayDropsColdAndStaleChunks() {
    TickSpikeReplay replay = new TickSpikeReplay(TickSpikeReplay.DEFAULT_SETTINGS);
    replay.reset(0L);
    replay.addHeat(WORLD, 0, 0, 100D, 0L);
    replay.addHeat(WORLD, 1, 1, 0.16D, 0L);
    replay.decayIfDue(100L);
    Assertions.assertEquals(100D, replay.heat(WORLD, 0, 0), "not due yet");
    replay.decayIfDue(500L);
    Assertions.assertEquals(90D, replay.heat(WORLD, 0, 0), 1.0E-9D);
    Assertions.assertEquals(0D, replay.heat(WORLD, 1, 1));
    replay.decayIfDue(200_000L);
    Assertions.assertEquals(0, replay.trackedChunks());
  }

  @Test
  void stopsTrackingNewChunksAtTheCap() {
    TickSpikeReplay replay = new TickSpikeReplay(new TickSpikeReplay.Settings() {
      @Override
      public int maxTrackedChunks() {
        return 2;
      }
    });
    replay.captureUniform(WORLD, 0, 0, 60D, 0L);
    Assertions.assertEquals(2, replay.trackedChunks());
  }
}
