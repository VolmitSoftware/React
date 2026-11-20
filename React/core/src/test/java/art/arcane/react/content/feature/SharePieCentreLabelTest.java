package art.arcane.react.content.feature;

import art.arcane.react.api.rendering.MapGlyphs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharePieCentreLabelTest {
  private static final int SINGLE_MAP_RADIUS = 21;
  private static final int LINE_HEIGHT = MapGlyphs.FONT_HEIGHT + 1;

  @Test
  void totalThatFitsKeepsItsDecimalAndTheDefaultHole() {
    SharePieRenderer.CentreLabel label = SharePieRenderer.centreLabel(SINGLE_MAP_RADIUS, "42", "42", MapGlyphs::runWidth, LINE_HEIGHT,
        MapGlyphs.FONT_HEIGHT);
    assertEquals(new SharePieRenderer.CentreLabel(8, "42"), label);
  }

  @Test
  void holeGrowsToKeepTheDecimalOnASingleMap() {
    SharePieRenderer.CentreLabel label = SharePieRenderer.centreLabel(SINGLE_MAP_RADIUS,
        SharePieRenderer.compactCount(3_300_000L), SharePieRenderer.compactCountShort(3_300_000L), MapGlyphs::runWidth, LINE_HEIGHT,
        MapGlyphs.FONT_HEIGHT);
    assertEquals("3.3m", label.value());
    assertTrue(label.innerRadius() > 8, "inner " + label.innerRadius());
    assertFits(label);
  }

  @Test
  void decimalDropsWhenNoHoleFitsIt() {
    SharePieRenderer.CentreLabel label = SharePieRenderer.centreLabel(SINGLE_MAP_RADIUS,
        SharePieRenderer.compactCount(45_600L), SharePieRenderer.compactCountShort(45_600L), MapGlyphs::runWidth, LINE_HEIGHT,
        MapGlyphs.FONT_HEIGHT);
    assertEquals("46k", label.value());
    assertTrue(label.innerRadius() > 8 && label.innerRadius() <= (SINGLE_MAP_RADIUS * 3) / 5, "inner " + label.innerRadius());
    assertFits(label);
  }

  @Test
  void hundredsOfThousandsStillFitOnASingleMap() {
    SharePieRenderer.CentreLabel label = SharePieRenderer.centreLabel(SINGLE_MAP_RADIUS,
        SharePieRenderer.compactCount(833_500L), SharePieRenderer.compactCountShort(833_500L), MapGlyphs::runWidth, LINE_HEIGHT,
        MapGlyphs.FONT_HEIGHT);
    assertEquals("834k", label.value());
    assertFits(label);
  }

  @Test
  void totalThatNeverFitsIsLeftToTheFooter() {
    SharePieRenderer.CentreLabel label = SharePieRenderer.centreLabel(10,
        SharePieRenderer.compactCount(833_500L), SharePieRenderer.compactCountShort(833_500L), MapGlyphs::runWidth, LINE_HEIGHT,
        MapGlyphs.FONT_HEIGHT);
    assertEquals(new SharePieRenderer.CentreLabel(4, ""), label);
  }

  @Test
  void largePiesKeepTheFullTotal() {
    SharePieRenderer.CentreLabel label = SharePieRenderer.centreLabel(60,
        SharePieRenderer.compactCount(3_300_000L), SharePieRenderer.compactCountShort(3_300_000L), MapGlyphs::runWidth, LINE_HEIGHT,
        MapGlyphs.FONT_HEIGHT);
    assertEquals(new SharePieRenderer.CentreLabel(24, "3.3m"), label);
  }

  @Test
  void shortFormRoundsToTheNearestUnit() {
    assertEquals("4m", SharePieRenderer.compactCountShort(3_600_000L));
    assertEquals("1m", SharePieRenderer.compactCountShort(999_700L));
    assertEquals("13k", SharePieRenderer.compactCountShort(12_500L));
    assertEquals("999", SharePieRenderer.compactCountShort(999L));
  }

  private static void assertFits(SharePieRenderer.CentreLabel label) {
    int room = SharePieRenderer.labelRoom(label.innerRadius(), LINE_HEIGHT, MapGlyphs.FONT_HEIGHT);
    assertTrue(MapGlyphs.runWidth(label.value()) <= room, label + " in " + room);
  }
}
