package art.arcane.react.content.directorcommand;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.model.SampledChunk;
import com.google.common.util.concurrent.AtomicDouble;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class ChunkCommandsTest {
  private static Sampler sampler(String id) {
    return new Sampler() {
      @Override
      public double sample() {
        return 0D;
      }

      @Override
      public String getId() {
        return id;
      }

      @Override
      public String formattedValue(double t) {
        return String.valueOf((long) t);
      }

      @Override
      public String formattedSuffix(double t) {
        return "ms";
      }

      @Override
      public void start() {
      }

      @Override
      public void stop() {
      }
    };
  }

  private static SampledChunk chunk() {
    return new SampledChunk(UUID.randomUUID(), "minecraft:overworld", 0, 0);
  }

  @Test
  void emptyChunkSendsNothing() {
    RecordingSender sender = new RecordingSender();

    ChunkCommands.sendSamples(sender, chunk(), (String id) -> sampler(id));

    Assertions.assertTrue(sender.lines.isEmpty());
  }

  @Test
  void sampleLineShowsSamplerNameAndFormattedValue() {
    RecordingSender sender = new RecordingSender();
    SampledChunk chunk = chunk();
    chunk.get("tick-time").set(42D);

    ChunkCommands.sendSamples(sender, chunk, (String id) -> sampler(id));

    Assertions.assertEquals(java.util.List.of("Tick Time: 42 ms"), sender.lines);
  }

  @Test
  void unknownSamplersAreSkipped() {
    RecordingSender sender = new RecordingSender();
    SampledChunk chunk = chunk();
    chunk.get("gone").set(5D);
    chunk.get("entities").set(7D);
    Map<String, Sampler> known = Map.of("entities", sampler("entities"));

    ChunkCommands.sendSamples(sender, chunk, known::get);

    Assertions.assertEquals(java.util.List.of("Entities: 7 ms"), sender.lines);
  }

  @Test
  void everyKnownSamplerGetsExactlyOneLine() {
    RecordingSender sender = new RecordingSender();
    SampledChunk chunk = chunk();
    chunk.get("a").set(1D);
    chunk.get("b").set(2D);
    chunk.get("c").set(3D);

    ChunkCommands.sendSamples(sender, chunk, (String id) -> sampler(id));

    Assertions.assertEquals(Set.of("A: 1 ms", "B: 2 ms", "C: 3 ms"), new HashSet<>(sender.lines));
    Assertions.assertEquals(3, sender.lines.size());
  }

  @Test
  void nullSenderIsIgnored() {
    SampledChunk chunk = chunk();
    chunk.get("a").set(1D);
    AtomicDouble value = chunk.get("a");

    Assertions.assertDoesNotThrow(() -> ChunkCommands.sendSamples(null, chunk, (String id) -> sampler(id)));
    Assertions.assertEquals(1D, value.get());
  }
}
