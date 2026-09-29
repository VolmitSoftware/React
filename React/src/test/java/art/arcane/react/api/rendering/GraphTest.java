package art.arcane.react.api.rendering;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GraphTest {

  @Test
  void storedSamplesAreRawSoTheHeaderAndExtremesShowRealReadings() {
    Graph graph = new Graph();
    graph.push(10D);
    graph.push(40D);
    graph.push(100D);

    Assertions.assertEquals(100D, graph.get(0), 1.0E-9D);
    Assertions.assertEquals(40D, graph.get(1), 1.0E-9D);
    Assertions.assertEquals(100D, graph.getMax(3), 1.0E-9D);
    Assertions.assertEquals(10D, graph.getMin(3), 1.0E-9D);
  }

  @Test
  void plottedLineIsATrailingThreeSampleAverage() {
    Graph graph = new Graph();
    graph.push(10D);
    graph.push(40D);
    graph.push(100D);

    Assertions.assertEquals(50D, graph.getSmoothed(0), 1.0E-9D);
    Assertions.assertEquals(25D, graph.getSmoothed(1), 1.0E-9D);
    Assertions.assertEquals(10D, graph.getSmoothed(2), 1.0E-9D);
  }

  @Test
  void emptyGraphReadsZero() {
    Graph graph = new Graph();

    Assertions.assertEquals(0D, graph.get(0), 1.0E-9D);
    Assertions.assertEquals(0D, graph.getSmoothed(0), 1.0E-9D);
  }
}
