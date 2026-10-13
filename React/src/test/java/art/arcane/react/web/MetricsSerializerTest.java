package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.MetricsSerializer;
import art.arcane.react.api.web.dto.SamplerDto;
import art.arcane.react.core.history.MetricSnapshotValue;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetricsSerializerTest {
  @Test
  void serializesSourceMetadataWithoutReplacingItsTimestamp() {
    SamplerDto value = new MetricsSerializer().toDto(new FakeSampler());

    assertEquals("tick-time", value.id);
    assertEquals(42D, value.value);
    assertEquals("ms", value.suffix);
    assertEquals("42.0", value.display);
    assertTrue(value.available);
    assertEquals(123L, value.sampledAtMs);
    assertEquals(500L, value.intervalMs);
    assertEquals(1_000L, value.windowMs);
    assertEquals(Sampler.Quality.MEASURED, value.quality);
    assertEquals("ms", value.unit);
    assertEquals("", value.unavailableReason);
  }

  @Test
  void omitsOnlyUnsupportedDiagnosticsFromLiveJson() {
    MetricsSerializer serializer = new MetricsSerializer();
    Gson gson = new Gson();
    JsonObject unsupported = JsonParser.parseString(gson.toJson(serializer.toDto(new FakeSampler()))).getAsJsonObject();
    assertFalse(unsupported.has("diagnostics"));

    Sampler.Diagnostics zero = new Sampler.Diagnostics(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    Sampler.Diagnostics measured = new Sampler.Diagnostics(2L, 1L, 1L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
    for (Sampler.Diagnostics diagnostics : new Sampler.Diagnostics[]{zero, measured}) {
      SamplerDto dto = serializer.toDto(new MetricSnapshotValue("tick-time", "Tick Time", "ms", 42D,
          "42", true, 123L, Sampler.Metadata.UNKNOWN, "", diagnostics));
      assertSame(diagnostics, dto.diagnostics);
      JsonObject retained = JsonParser.parseString(gson.toJson(dto)).getAsJsonObject().getAsJsonObject("diagnostics");
      assertEquals(12, retained.size());
      assertEquals(diagnostics.attempts(), retained.get("attempts").getAsLong());
      assertEquals(diagnostics.totalQueueNanos(), retained.get("totalQueueNanos").getAsLong());
    }
  }

  private static final class FakeSampler implements Sampler {
    @Override
    public String getId() {
      return "tick-time";
    }

    @Override
    public String getName() {
      return "Tick Time";
    }

    @Override
    public double sample() {
      return 42D;
    }

    @Override
    public Reading captureReading() {
      return new Reading(42D, 123L, true, new Metadata(500L, 1_000L, Quality.MEASURED, "ms"), "");
    }

    @Override
    public String formattedValue(double sampled) {
      return String.valueOf(sampled);
    }

    @Override
    public String formattedSuffix(double sampled) {
      return "ms";
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void render() {
    }
  }
}
