package art.arcane.react.core.controller;

import art.arcane.react.api.web.WebRevisions;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HotloadRevisionRoutingTest {
  private static final File ROOT = new File("build/hotload-revision-test/plugins/React");

  @Test
  void appliedHotloadsMoveTheRevisionOfWhatTheyChanged() {
    WebRevisions.Snapshot start = WebRevisions.current();

    HotloadController.bumpWebRevisionsForAppliedFile(ROOT, new File(ROOT, "tweak/fast-snow.toml"));
    HotloadController.bumpWebRevisionsForAppliedFile(ROOT, new File(ROOT, "feature/mob-stacking.toml"));
    HotloadController.bumpWebRevisionsForAppliedFile(ROOT, new File(ROOT, "feature/per-world-tick-budget.toml"));
    HotloadController.bumpWebRevisionsForAppliedFile(ROOT, new File(ROOT, "react.toml"));
    HotloadController.bumpWebRevisionsForAppliedFile(ROOT, new File(ROOT, "action/collect-garbage.toml"));
    HotloadController.bumpWebRevisionsForAppliedFile(ROOT, new File(ROOT, "languages/en_US.toml"));
    HotloadController.bumpWebRevisionsForAppliedFile(ROOT, new File(ROOT, "web.toml"));

    WebRevisions.Snapshot end = WebRevisions.current();
    assertEquals(start.tweaks() + 1L, end.tweaks());
    assertEquals(start.features() + 2L, end.features());
    assertEquals(start.worlds() + 1L, end.worlds());
    assertEquals(start.config() + 1L, end.config());
    assertEquals(start.actions() + 1L, end.actions());
    assertEquals(start.packs(), end.packs());
  }
}
