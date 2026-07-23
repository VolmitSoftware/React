package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.lang.management.ManagementFactory;

public class SamplerJvmThreads extends ReactCachedSampler {
  public static final String ID = "jvm-threads";

  public SamplerJvmThreads() {
    super(ID, 1000);
  }

  @Override
  public Material getIcon() {
    return Material.STRING;
  }

  @Override
  public double onSample() {
    return ManagementFactory.getThreadMXBean().getThreadCount();
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(Math.round(t));
  }

  @Override
  public String formattedSuffix(double t) {
    return " threads";
  }
}
