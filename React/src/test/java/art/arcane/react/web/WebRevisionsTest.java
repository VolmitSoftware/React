package art.arcane.react.web;

import art.arcane.react.api.web.WebRevisions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebRevisionsTest {

  @Test
  void countersStartAtTheClockSoARestartNeverRepeatsAValue() {
    WebRevisions.Snapshot current = WebRevisions.current();
    assertTrue(current.features() > 1_600_000_000_000L);
    assertTrue(current.actions() > 1_600_000_000_000L);
  }

  @Test
  void unchangedCountersKeepTheSameSnapshotInstance() {
    assertSame(WebRevisions.current(), WebRevisions.current());
  }

  @Test
  void bumpMovesOnlyThatKind() {
    WebRevisions.Snapshot before = WebRevisions.current();
    WebRevisions.bump(WebRevisions.Kind.PACKS);
    WebRevisions.Snapshot after = WebRevisions.current();
    assertEquals(before.packs() + 1L, after.packs());
    assertEquals(before.features(), after.features());
    assertEquals(before.tweaks(), after.tweaks());
    assertEquals(before.config(), after.config());
    assertEquals(before.worlds(), after.worlds());
    assertEquals(before.actions(), after.actions());
  }

  @Test
  void configPathsMoveTheKindTheyBelongTo() {
    WebRevisions.Snapshot start = WebRevisions.current();
    WebRevisions.bumpForConfigPath("feature.fast-leaf-decay.enabled");
    WebRevisions.bumpForConfigPath("tweak.fast-snow.enabled");
    WebRevisions.bumpForConfigPath("main.language");
    WebRevisions.bumpForConfigPath("core.hotload.enabled");
    WebRevisions.bumpForConfigPath("action.collect-garbage.enabled");
    WebRevisions.bumpForConfigPath("feature.per-world-tick-budget.worldOverrides");
    WebRevisions.bumpForConfigPath("unknown.thing");
    WebRevisions.bumpForConfigPath("");
    WebRevisions.Snapshot end = WebRevisions.current();
    assertEquals(start.features() + 2L, end.features());
    assertEquals(start.tweaks() + 1L, end.tweaks());
    assertEquals(start.config() + 2L, end.config());
    assertEquals(start.actions() + 1L, end.actions());
    assertEquals(start.worlds() + 1L, end.worlds());
    assertEquals(start.packs(), end.packs());
  }
}
