package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.ReactCachedRateSampler;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

public class SamplerChunkUnloads extends ReactCachedRateSampler implements Listener {
  public static final String ID = "chunk-unloads";

  public SamplerChunkUnloads() {
    super(ID, 1000);
  }

  @Override
  public String getIcon() {
    return "minecraft:ice";
  }

  @EventHandler
  public void on(ChunkUnloadEvent event) {
    increment();
    getChunkCounter(event.getChunk()).addAndGet(1D);
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 1);
  }

  @Override
  public String formattedSuffix(double t) {
    return "/s";
  }
}
