package art.arcane.react.web;

import art.arcane.react.api.rendering.Graph;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.MetricsSerializer;
import art.arcane.react.api.web.dto.SamplerDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetricsSerializerTest {

  @Test
  void serializesValueHistoryAndExtremes() {
    Sampler fake = new FakeSampler("tick-time", 42.0, "ms", new double[]{40, 41, 42, 43, 42, 41, 40, 42});
    SamplerDto d = new MetricsSerializer().toDto(fake);
    assertEquals("tick-time", d.id);
    assertEquals(42.0, d.value, 1e-9);
    assertEquals("ms", d.suffix);
    assertEquals("42.0", d.display);
    assertEquals(8, d.history.length);
    assertEquals(43.0, d.max, 1e-9);
    assertEquals(40.0, d.min, 1e-9);
  }

  private static final class FakeSampler implements Sampler {
    private final String id;
    private final double value;
    private final String suffix;

    FakeSampler(String id, double value, String suffix, double[] history) {
      this.id = id;
      this.value = value;
      this.suffix = suffix;
      try {
        Graph g = injectGraph(id, history);
        Field lastPushField = Graph.class.getDeclaredField("lastPushMs");
        lastPushField.setAccessible(true);
        lastPushField.set(g, System.currentTimeMillis());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @SuppressWarnings("unchecked")
    private static Graph injectGraph(String name, double[] history) throws Exception {
      Field graphsField = Graph.class.getDeclaredField("graphs");
      graphsField.setAccessible(true);
      ConcurrentHashMap<String, Graph> graphsMap =
          (ConcurrentHashMap<String, Graph>) graphsField.get(null);
      Graph g = new Graph();
      Field seqField = Graph.class.getDeclaredField("sequence");
      seqField.setAccessible(true);
      double[] seq = (double[]) seqField.get(g);
      for (int i = 0; i < history.length; i++) {
        seq[i] = history[i];
      }
      Field headField = Graph.class.getDeclaredField("head");
      headField.setAccessible(true);
      headField.set(g, history.length);
      Field sizeField = Graph.class.getDeclaredField("size");
      sizeField.setAccessible(true);
      sizeField.set(g, history.length);
      graphsMap.put(name, g);
      return g;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getName() {
      return id;
    }

    @Override
    public double sample() {
      return value;
    }

    @Override
    public String formattedValue(double t) {
      return String.valueOf(t);
    }

    @Override
    public String formattedSuffix(double t) {
      return suffix;
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public void render() {}
  }
}
