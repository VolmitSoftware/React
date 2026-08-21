package art.arcane.react.api.benchmark;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BenchmarkRatingTest {
  @Test
  public void referenceScoreIsRatedFast() {
    Assertions.assertEquals(BenchmarkRating.FAST, BenchmarkRating.of(BenchmarkScale.REFERENCE_SCORE));
  }

  @Test
  public void ratingsCoverTheWholeScoreRange() {
    Assertions.assertEquals(BenchmarkRating.ULTRA_SLOW, BenchmarkRating.of(0));
    Assertions.assertEquals(BenchmarkRating.ULTRA_SLOW, BenchmarkRating.of(29));
    Assertions.assertEquals(BenchmarkRating.VERY_SLOW, BenchmarkRating.of(30));
    Assertions.assertEquals(BenchmarkRating.AVERAGE, BenchmarkRating.of(60));
    Assertions.assertEquals(BenchmarkRating.GOOD, BenchmarkRating.of(80));
    Assertions.assertEquals(BenchmarkRating.VERY_FAST, BenchmarkRating.of(125));
    Assertions.assertEquals(BenchmarkRating.ULTRA_FAST, BenchmarkRating.of(160));
    Assertions.assertEquals(BenchmarkRating.INSANELY_FAST, BenchmarkRating.of(200));
    Assertions.assertEquals(BenchmarkRating.INSANELY_FAST, BenchmarkRating.of(BenchmarkScale.MAXIMUM_SCORE));
  }

  @Test
  public void ratingNeverDegradesAsScoreRises() {
    BenchmarkRating previous = BenchmarkRating.of(0);
    for (int score = 1; score <= BenchmarkScale.MAXIMUM_SCORE; score++) {
      BenchmarkRating current = BenchmarkRating.of(score);
      Assertions.assertTrue(current.ordinal() >= previous.ordinal(), "regressed at " + score);
      previous = current;
    }
  }

  @Test
  public void fastLowerIsBetterMeasurementsAreNeverRatedSlow() {
    int fastFlush = BenchmarkScale.lowerIsBetter(0.4, BenchmarkScale.DRIVE_FLUSH_MILLIS);
    int fastLatency = BenchmarkScale.lowerIsBetter(9.0, BenchmarkScale.MEMORY_LATENCY_NANOS);

    Assertions.assertTrue(BenchmarkRating.of(fastFlush).ordinal() >= BenchmarkRating.FAST.ordinal());
    Assertions.assertTrue(BenchmarkRating.of(fastLatency).ordinal() >= BenchmarkRating.FAST.ordinal());
  }

  @Test
  public void slowLowerIsBetterMeasurementsAreNeverRatedFast() {
    int slowFlush = BenchmarkScale.lowerIsBetter(45.0, BenchmarkScale.DRIVE_FLUSH_MILLIS);
    int slowLatency = BenchmarkScale.lowerIsBetter(180.0, BenchmarkScale.MEMORY_LATENCY_NANOS);

    Assertions.assertTrue(BenchmarkRating.of(slowFlush).ordinal() <= BenchmarkRating.SLOW.ordinal());
    Assertions.assertTrue(BenchmarkRating.of(slowLatency).ordinal() <= BenchmarkRating.SLOW.ordinal());
  }

  @Test
  public void everyRatingCarriesADistinctMessageKey() {
    for (BenchmarkRating rating : BenchmarkRating.values()) {
      Assertions.assertNotNull(rating.message());
      Assertions.assertFalse(rating.color().isBlank());
      for (BenchmarkRating other : BenchmarkRating.values()) {
        if (other != rating) {
          Assertions.assertNotEquals(rating.message().id(), other.message().id());
        }
      }
    }
  }
}
