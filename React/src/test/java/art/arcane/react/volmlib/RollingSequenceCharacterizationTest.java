package art.arcane.react.volmlib;

import art.arcane.volmlib.util.math.RollingSequence;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Most expected values come from Chrono 22.9.10, the library React used before it moved to VolmLib.
 * Scenarios where VolmLib intentionally differs (a fully zero window resets the running sum, which
 * clears float residue and non-finite poison) are pinned from VolmLib in a second file.
 */
class RollingSequenceCharacterizationTest {
  private static final String CHRONO_GOLDEN = "/art/arcane/react/volmlib/rolling-sequence-chrono-22.9.10.txt";
  private static final String VOLMLIB_GOLDEN = "/art/arcane/react/volmlib/rolling-sequence-volmlib.txt";
  private static final Map<String, String> GROUPS = Map.of(
      "avg", "average", "dirty", "average", "size", "average",
      "min", "extremes", "max", "extremes",
      "last1", "addLast", "lastW", "addLast", "lastOver", "addLast");

  @TestFactory
  List<DynamicTest> matchesGoldens() throws IOException {
    Map<String, List<String>> expected = readGoldens(CHRONO_GOLDEN);
    for (Map.Entry<String, List<String>> scenario : readGoldens(VOLMLIB_GOLDEN).entrySet()) {
      Assertions.assertNull(expected.put(scenario.getKey(), scenario.getValue()), scenario.getKey() + " is in both golden files");
    }
    Map<String, List<String>> actual = RollingSequenceScenarios.run(VolmLibAdapters::sequence);
    Assertions.assertEquals(actual.keySet(), expected.keySet(), "scenario set drifted from the golden files");

    List<DynamicTest> tests = new ArrayList<>();
    for (Map.Entry<String, List<String>> scenario : expected.entrySet()) {
      List<String> actualLines = actual.get(scenario.getKey());
      for (String group : List.of("average", "extremes", "addLast")) {
        List<String> expectedView = project(scenario.getValue(), group);
        if (expectedView.stream().allMatch(line -> line.indexOf('=') < 0)) {
          continue;
        }
        tests.add(DynamicTest.dynamicTest(scenario.getKey() + " " + group,
            () -> assertSameLines(scenario.getKey(), group, expectedView, project(actualLines, group))));
      }
    }
    return tests;
  }

  @Test
  void firstValueFillsTheWholeWindow() {
    RollingSequence sequence = new RollingSequence(4);
    Assertions.assertEquals(0D, sequence.getAverage());

    sequence.put(8D);

    Assertions.assertEquals(8D, sequence.getAverage());
    Assertions.assertEquals(32D, sequence.addLast(4));
  }

  @Test
  void laterValuesReplaceOneSlotAtATime() {
    RollingSequence sequence = new RollingSequence(4);
    double[] expected = {1D, 1.25D, 1.75D, 2.5D, 3.5D, 4.5D};

    for (int index = 0; index < expected.length; index++) {
      sequence.put(index + 1D);
      Assertions.assertEquals(expected[index], sequence.getAverage(), "after value " + (index + 1));
    }
  }

  @Test
  void extremesStayStaleUntilMoreThanAWindowOfPuts() {
    RollingSequence sequence = new RollingSequence(3);
    sequence.put(5D);
    sequence.put(6D);
    sequence.put(7D);
    Assertions.assertEquals(0D, sequence.getMin());
    Assertions.assertEquals(7D, sequence.getMax());

    sequence.put(8D);

    Assertions.assertEquals(6D, sequence.getMin());
    Assertions.assertEquals(8D, sequence.getMax());
  }

  @Test
  void precisionModeRecomputesExtremesOnEveryRead() {
    RollingSequence sequence = new RollingSequence(3);
    sequence.setPrecision(true);
    sequence.put(5D);

    Assertions.assertTrue(sequence.isPrecision());
    Assertions.assertEquals(5D, sequence.getMin());
    Assertions.assertEquals(5D, sequence.getMax());
  }

  @Test
  void extremesClampToTheIntegerRange() {
    RollingSequence sequence = new RollingSequence(2);
    sequence.put(1.0E17D);
    sequence.resetExtremes();
    Assertions.assertEquals((double) Integer.MAX_VALUE, sequence.getMin());

    sequence.put(-1.0E17D);
    sequence.put(-1.0E17D);
    sequence.resetExtremes();
    Assertions.assertEquals((double) Integer.MIN_VALUE, sequence.getMax());
  }

  @Test
  void notANumberLeavesTheAverageAfterAFullyZeroWindow() {
    RollingSequence sequence = new RollingSequence(3);
    sequence.put(1D);
    sequence.put(Double.NaN);
    sequence.put(0D);
    sequence.put(0D);
    Assertions.assertTrue(Double.isNaN(sequence.getAverage()), "average was " + sequence.getAverage());

    sequence.put(0D);
    Assertions.assertEquals(0D, sequence.getAverage());

    sequence.put(9D);
    Assertions.assertEquals(3D, sequence.getAverage());
  }

  @Test
  void notANumberStaysInTheAverageWhileTheWindowIsNeverFullyZero() {
    RollingSequence sequence = new RollingSequence(3);
    sequence.put(1D);
    sequence.put(Double.NaN);
    for (int index = 0; index < 9; index++) {
      sequence.put(index % 3 == 0 ? 5D : 0D);
    }

    Assertions.assertTrue(Double.isNaN(sequence.getAverage()), "average was " + sequence.getAverage());
  }

  @Test
  void infinityLeavesTheAverageAfterAFullyZeroWindow() {
    RollingSequence sequence = new RollingSequence(3);
    sequence.put(1D);
    sequence.put(Double.POSITIVE_INFINITY);
    Assertions.assertEquals(Double.POSITIVE_INFINITY, sequence.getAverage());
    sequence.put(0D);
    sequence.put(0D);
    sequence.put(0D);
    Assertions.assertEquals(0D, sequence.getAverage());

    sequence.put(9D);
    Assertions.assertEquals(3D, sequence.getAverage());
  }

  @Test
  void idleWindowAveragesExactlyZero() {
    RollingSequence sequence = new RollingSequence(2);
    double[] values = {0D, 0.1D, 0.2D, 0.30000000000000004D, 0D, 0D, 0D};
    for (double value : values) {
      sequence.put(value);
    }

    Assertions.assertEquals(0D, sequence.getAverage());
  }

  @Test
  void idleWindowAveragesExactlyZeroAfterLargeValues() {
    RollingSequence sequence = new RollingSequence(100);
    for (int index = 0; index < 102; index++) {
      sequence.put(1.0E17D + (index * 0.1D));
    }
    for (int index = 0; index < 200; index++) {
      sequence.put(0D);
    }

    Assertions.assertEquals(0D, sequence.getAverage());
  }

  private static void assertSameLines(String scenario, String group, List<String> expected, List<String> actual) {
    Assertions.assertEquals(expected.size(), actual.size(), scenario + " step count");
    int differing = 0;
    String first = null;
    for (int index = 0; index < expected.size(); index++) {
      if (!expected.get(index).equals(actual.get(index))) {
        differing++;
        if (first == null) {
          first = "step " + expected.get(index) + " but got " + actual.get(index);
        }
      }
    }
    if (differing > 0) {
      Assertions.fail(scenario + " " + group + ": " + differing + " of " + expected.size()
          + " observations differ from the golden file; first at " + first);
    }
  }

  private static List<String> project(List<String> lines, String group) {
    List<String> view = new ArrayList<>();
    for (String line : lines) {
      String[] tokens = line.split(" ");
      StringBuilder kept = new StringBuilder(tokens[0]);
      for (int index = 1; index < tokens.length; index++) {
        String key = tokens[index].substring(0, tokens[index].indexOf('='));
        if (group.equals(GROUPS.get(key))) {
          kept.append(' ').append(tokens[index]);
        }
      }
      view.add(kept.toString());
    }
    return view;
  }

  private static Map<String, List<String>> readGoldens(String resource) throws IOException {
    Map<String, List<String>> goldens = new LinkedHashMap<>();
    try (InputStream stream = RollingSequenceCharacterizationTest.class.getResourceAsStream(resource)) {
      Assertions.assertNotNull(stream, "missing " + resource);
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
      String line;
      while ((line = reader.readLine()) != null) {
        int tab = line.indexOf('\t');
        goldens.computeIfAbsent(line.substring(0, tab), key -> new ArrayList<>()).add(line.substring(tab + 1));
      }
    }
    return goldens;
  }
}
