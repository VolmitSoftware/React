package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Bukkit;
import org.bukkit.Material;

public class SamplerWorlds extends ReactCachedSampler {
  public static final String ID = "worlds";

  public SamplerWorlds() {
    super(ID, 5000);
  }

  @Override
  public Material getIcon() {
    return Material.GLOBE_BANNER_PATTERN;
  }

  @Override
  public double onSample() {
    return Bukkit.getWorlds().size();
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(Math.round(t));
  }

  @Override
  public String formattedSuffix(double t) {
    return " worlds";
  }
}
