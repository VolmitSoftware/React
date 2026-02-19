package art.arcane.react.content.feature;

import art.arcane.react.util.data.TinyColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Iris World Chunk Share Pie Map feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureIrisWorldChunkSharePieMap extends FeatureIrisChunkSharePieBase {
  public static final String ID = "iris-world-chunk-share-pie-map";

  public FeatureIrisWorldChunkSharePieMap() {
    super(ID);
  }

  @Override
  protected String title() {
    return "World Chunk Share";
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(72, 132, 194);
  }

  @Override
  protected Map<String, Long> collectBuckets(Player viewer) {
    Map<String, Long> counts = newCounterMap();
    for (World world : Bukkit.getWorlds()) {
      if (world == null) {
        continue;
      }

      counts.put(displayName(world.getName()), (long) world.getLoadedChunks().length);
    }
    return counts;
  }
}
