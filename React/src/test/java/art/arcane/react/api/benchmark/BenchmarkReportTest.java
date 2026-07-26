package art.arcane.react.api.benchmark;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.BenchmarkMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BenchmarkReportTest {
  private static final int BAR_CELLS = 14;

  @Test
  public void barFillsProportionallyToTheScore() {
    Assertions.assertEquals(0, filledCells(BenchmarkReport.bar(0, BenchmarkRating.of(0))));
    Assertions.assertEquals(BAR_CELLS / 2, filledCells(BenchmarkReport.bar(100, BenchmarkRating.of(100))));
    Assertions.assertEquals(BAR_CELLS, filledCells(BenchmarkReport.bar(200, BenchmarkRating.of(200))));
    Assertions.assertEquals(BAR_CELLS, filledCells(BenchmarkReport.bar(999, BenchmarkRating.of(999))));
  }

  @Test
  public void barAlwaysRendersEveryCell() {
    for (int score = 0; score <= BenchmarkScale.MAXIMUM_SCORE; score++) {
      String bar = BenchmarkReport.bar(score, BenchmarkRating.of(score));
      Assertions.assertEquals(BAR_CELLS, filledCells(bar) + emptyCells(bar), "cell drift at " + score);
    }
  }

  @Test
  public void barCarriesTheRatingColour() {
    String bar = BenchmarkReport.bar(150, BenchmarkRating.of(150));

    Assertions.assertTrue(bar.startsWith("<" + BenchmarkRating.of(150).color() + ">"), bar);
    Assertions.assertTrue(bar.endsWith("</dark_gray>"), bar);
  }

  @Test
  public void ratingWrapsPlainTextInItsColour() {
    String rendered = BenchmarkReport.rating(BenchmarkRating.FAST);

    Assertions.assertEquals("<green>Fast</green>", rendered);
  }

  @Test
  public void scoredLineRendersThroughTheStrictMiniMessagePipeline() {
    int score = 137;
    BenchmarkRating rating = BenchmarkRating.of(score);

    String rendered = ReactLanguage.plain(
        BenchmarkMessages.LINE_SCORED,
        MessageArgument.untrusted("label", ReactLanguage.plain(BenchmarkMessages.LABEL_CPU_INTEGER)),
        MessageArgument.untrusted("value", "258.4 Mop/s"),
        MessageArgument.trusted("bar", BenchmarkReport.bar(score, rating)),
        MessageArgument.untrusted("score", score),
        MessageArgument.trusted("rating", BenchmarkReport.rating(rating))
    );

    Assertions.assertTrue(rendered.contains("Integer"), rendered);
    Assertions.assertTrue(rendered.contains("258.4 Mop/s"), rendered);
    Assertions.assertTrue(rendered.contains("137"), rendered);
    Assertions.assertTrue(rendered.contains("Very Fast"), rendered);
    Assertions.assertFalse(rendered.contains("<"), rendered);
    Assertions.assertFalse(rendered.contains("§"), rendered);
  }

  @Test
  public void everyRatingRendersWithoutTrippingStrictParsing() {
    for (BenchmarkRating rating : BenchmarkRating.values()) {
      String rendered = ReactLanguage.plain(
          BenchmarkMessages.OVERALL,
          MessageArgument.untrusted("label", ReactLanguage.plain(BenchmarkMessages.LABEL_OVERALL)),
          MessageArgument.untrusted("score", rating.minimumScore()),
          MessageArgument.trusted("rating", BenchmarkReport.rating(rating)),
          MessageArgument.untrusted("duration", "3.7s")
      );

      Assertions.assertTrue(rendered.contains(ReactLanguage.plain(rating.message())), rendered);
    }
  }

  private int filledCells(String bar) {
    return count(bar, '█');
  }

  private int emptyCells(String bar) {
    return count(bar, '░');
  }

  private int count(String value, char target) {
    int total = 0;
    for (int index = 0; index < value.length(); index++) {
      if (value.charAt(index) == target) {
        total++;
      }
    }
    return total;
  }
}
