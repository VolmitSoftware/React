package art.arcane.react.content.directorcommand;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.model.SampledChunk;
import art.arcane.volmlib.util.localization.MessageArgument;
import com.google.common.util.concurrent.AtomicDouble;

import java.util.Map;
import java.util.function.Function;

/**
 * The sampler lines of {@code /react chunk sample} and {@code /react chunk worst}.
 */
public final class ChunkCommands {
  private ChunkCommands() {
  }

  public static void sendSamples(Object sender, SampledChunk chunk) {
    sendSamples(sender, chunk, SamplerHosts::sampler);
  }

  static void sendSamples(Object sender, SampledChunk chunk, Function<String, Sampler> samplers) {
    for (Map.Entry<String, AtomicDouble> entry : chunk.getValues().entrySet()) {
      Sampler sampler = samplers.apply(entry.getKey());
      if (sampler == null) {
        continue;
      }
      ReactLanguage.send(sender, CommandMessages.CHUNK_SAMPLE,
          MessageArgument.untrusted("sampler", sampler.getName()),
          MessageArgument.untrusted("value", sampler.format(entry.getValue().get())));
    }
  }
}
