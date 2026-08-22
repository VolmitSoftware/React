package art.arcane.react.core.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotloadRevisionTrackerTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void newerTouchInvalidatesAPreparedRevisionForTheSameNormalizedPath() {
    HotloadRevisionTracker revisions = new HotloadRevisionTracker();
    Path config = temporaryDirectory.resolve("feature").resolve("probe.toml");
    Path equivalent = temporaryDirectory.resolve("feature").resolve(".").resolve("probe.toml");

    long preparedRevision = revisions.touch(config);
    assertTrue(revisions.isCurrent(config, preparedRevision));

    revisions.touch(equivalent);
    assertFalse(revisions.isCurrent(config, preparedRevision));
  }

  @Test
  void touchesToAnotherPathDoNotInvalidateThePreparedRevision() {
    HotloadRevisionTracker revisions = new HotloadRevisionTracker();
    Path prepared = temporaryDirectory.resolve("prepared.toml");
    Path other = temporaryDirectory.resolve("other.toml");

    long preparedRevision = revisions.touch(prepared);
    revisions.touch(other);

    assertTrue(revisions.isCurrent(prepared, preparedRevision));
  }

  @Test
  void stalePreparedRevisionCannotRunItsApplyCallback() {
    HotloadRevisionTracker revisions = new HotloadRevisionTracker();
    Path config = temporaryDirectory.resolve("feature.toml");
    AtomicInteger applyCalls = new AtomicInteger();
    long preparedRevision = revisions.touch(config);

    revisions.touch(config);
    HotloadRevisionTracker.GuardedBoolean result = revisions.runBooleanIfCurrent(config, preparedRevision, () -> {
      applyCalls.incrementAndGet();
      return true;
    });

    assertFalse(result.current());
    assertFalse(result.value());
    assertEquals(0, applyCalls.get());
  }
}
