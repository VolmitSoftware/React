package art.arcane.react.content.feature;

import art.arcane.react.api.web.profiler.SamplingProfiler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class ProfilerOwnerShareTest {
  @Test
  void stoppedProfilerHasNoShares() {
    SamplingProfiler profiler = new SamplingProfiler(Optional::empty);
    long[] now = {0L};
    ProfilerOwnerShare share = new ProfilerOwnerShare(() -> Optional.of(profiler), () -> now[0]);
    Assertions.assertTrue(share.get().isEmpty());
    Assertions.assertTrue(new ProfilerOwnerShare(Optional::empty, () -> now[0]).get().isEmpty());
  }

  @Test
  void runningProfilerSharesRefreshAfterTheInterval() {
    SamplingProfiler profiler = new SamplingProfiler(Optional::empty);
    long[] now = {0L};
    ProfilerOwnerShare share = new ProfilerOwnerShare(() -> Optional.of(profiler), () -> now[0]);
    Assertions.assertTrue(share.get().isEmpty());
    profiler.start(1, SamplingProfiler.ALL, 30);
    try {
      long deadline = System.currentTimeMillis() + 5_000L;
      while (profiler.owners(false).samples() == 0 && System.currentTimeMillis() < deadline) {
        Thread.onSpinWait();
      }
      Assertions.assertTrue(share.get().isEmpty(), "kept until the refresh interval passes");
      now[0] += ProfilerOwnerShare.REFRESH_MS;
      Assertions.assertFalse(share.get().isEmpty());
    } finally {
      profiler.stop();
    }
  }
}
