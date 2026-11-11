package art.arcane.react.volmlib;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;

/**
 * Deterministic RollingSequence scenarios. The same code produced the golden files from the
 * Chrono classes React used before the VolmLib swap, so it must stay library-neutral.
 */
final class RollingSequenceScenarios {
  static final int[] WINDOWS = {1, 2, 5, 20, 100};
  static final String[] SEQUENCES = {
      "random", "gaussian", "constant", "zeros", "increasing", "decreasing", "negative", "nanOnce",
      "infinityOnce", "huge", "hugeThenZero", "drift", "lexical", "negativeZero", "zeroFirst"
  };

  private static final Map<String, double[]> STREAMS = new ConcurrentHashMap<>();

  interface Seq {
    void put(double value);

    double getAverage();

    double getMin();

    double getMax();

    double addLast(int amount);

    void resetExtremes();

    void setPrecision(boolean precision);

    int size();

    boolean isDirty();
  }

  private RollingSequenceScenarios() {
  }

  static Map<String, List<String>> run(IntFunction<Seq> factory) {
    Map<String, List<String>> results = new LinkedHashMap<>();
    for (int window : WINDOWS) {
      for (String sequence : SEQUENCES) {
        results.put(sequence + "/w" + window, observed(factory.apply(window), sequence, window, -1));
      }
      Seq precise = factory.apply(window);
      precise.setPrecision(true);
      results.put("random-precision/w" + window, observed(precise, "random", window, -1));
      results.put("random-resetExtremes/w" + window, observed(factory.apply(window), "random", window, window + 1));
      results.put("random-averageOnly/w" + window, averageOnly(factory.apply(window), "random", window));
      results.put("negative-averageOnly/w" + window, averageOnly(factory.apply(window), "negative", window));
      results.put("fresh-resetExtremes/w" + window, freshReset(factory.apply(window)));
    }
    return results;
  }

  static int length(int window) {
    return (4 * window) + 8;
  }

  static double value(String sequence, int window, int index) {
    int length = length(window);
    switch (sequence) {
      case "random":
        return seeded(42L + window, index) * 100D;
      case "gaussian":
        return gaussian(7L + window, index) * 50D;
      case "constant":
        return 7.5D;
      case "zeros":
        return 0D;
      case "increasing":
        return index + 1D;
      case "decreasing":
        return length - index;
      case "negative":
        return -(index + 1D) * 1.5D;
      case "nanOnce":
        return poisoned(Double.NaN, window, index, length);
      case "infinityOnce":
        return poisoned(Double.POSITIVE_INFINITY, window, index, length);
      case "huge":
        return new double[]{1.0E300D, -1.0E300D, Double.MAX_VALUE, 1D, -Double.MAX_VALUE, 1.0E-300D, 3D}[index % 7];
      case "hugeThenZero":
        return index < window + 2 ? 1.0E17D + (index * 0.1D) : index >= length - 2 ? 1D : 0D;
      case "drift":
        return index < 2 * window ? 0.1D * (index % 7) : index >= length - 2 ? 0.3D : 0D;
      case "lexical":
        return new double[]{9D, 10D, 100D, 2D, 1.0E10D, -3D, 0.5D, 2.0E7D}[index % 8];
      case "negativeZero":
        return index % 5 == 3 ? 5D : index % 2 == 0 ? -0D : 0D;
      case "zeroFirst":
        return index;
      default:
        throw new IllegalArgumentException(sequence);
    }
  }

  private static double poisoned(double poison, int window, int index, int length) {
    if (index < 3) {
      return index + 1D;
    }
    if (index == 3) {
      return poison;
    }
    if (index <= window + 5) {
      return 5D;
    }
    return index >= length - 3 ? 2D : 0D;
  }

  private static double seeded(long seed, int index) {
    return stream(seed, false)[index];
  }

  private static double gaussian(long seed, int index) {
    return stream(seed, true)[index];
  }

  private static double[] stream(long seed, boolean gaussian) {
    return STREAMS.computeIfAbsent((gaussian ? "g" : "u") + seed, key -> {
      Random random = new Random(seed);
      double[] values = new double[length(WINDOWS[WINDOWS.length - 1])];
      for (int index = 0; index < values.length; index++) {
        values[index] = gaussian ? random.nextGaussian() : random.nextDouble();
      }
      return values;
    });
  }

  static boolean snapshotStep(int window, int index) {
    int length = length(window);
    if (window <= 5 || index < 4 || index >= length - 3) {
      return true;
    }
    if (Math.abs(index - window) <= 2 || (index >= (2 * window) - 1 && index <= (2 * window) + 2)) {
      return true;
    }
    return index % (window >= 100 ? 31 : 13) == 0;
  }

  private static List<String> observed(Seq seq, String sequence, int window, int resetAt) {
    List<String> lines = new ArrayList<>();
    lines.add("fresh " + snapshot(seq, window));
    for (int index = 0; index < length(window); index++) {
      seq.put(value(sequence, window, index));
      if (index == resetAt) {
        seq.resetExtremes();
      }
      if (snapshotStep(window, index)) {
        lines.add(index + " " + snapshot(seq, window));
      }
    }
    return lines;
  }

  private static List<String> averageOnly(Seq seq, String sequence, int window) {
    List<String> lines = new ArrayList<>();
    for (int index = 0; index < length(window); index++) {
      seq.put(value(sequence, window, index));
      double average = seq.getAverage();
      if (snapshotStep(window, index)) {
        lines.add(index + " avg=" + average);
      }
    }
    lines.add("end " + snapshot(seq, window));
    return lines;
  }

  private static List<String> freshReset(Seq seq) {
    List<String> lines = new ArrayList<>();
    seq.resetExtremes();
    lines.add("reset min=" + seq.getMin() + " max=" + seq.getMax() + " avg=" + seq.getAverage()
        + " size=" + seq.size() + " dirty=" + seq.isDirty());
    seq.put(-4D);
    lines.add("put min=" + seq.getMin() + " max=" + seq.getMax() + " avg=" + seq.getAverage());
    return lines;
  }

  private static String snapshot(Seq seq, int window) {
    boolean dirtyBefore = seq.isDirty();
    String average = Double.toString(seq.getAverage());
    return "avg=" + average
        + " min=" + seq.getMin()
        + " max=" + seq.getMax()
        + " last1=" + seq.addLast(1)
        + " lastW=" + seq.addLast(window)
        + " lastOver=" + seq.addLast(window + 5)
        + " dirty=" + dirtyBefore + ">" + seq.isDirty();
  }
}
