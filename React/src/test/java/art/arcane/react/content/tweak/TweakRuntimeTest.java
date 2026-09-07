package art.arcane.react.content.tweak;

import org.bukkit.Material;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TweakRuntimeTest {

  @Test
  public void fastFluidsAcceleratesBothFluidsByDefaultAndWhenBothFlagsEnabled() throws Exception {
    TweakFastFluids untouched = new TweakFastFluids();
    Assertions.assertTrue(decideFluid(untouched, Material.WATER));
    Assertions.assertTrue(decideFluid(untouched, Material.LAVA));

    TweakFastFluids tweak = new TweakFastFluids();
    setFluidFlags(tweak, true, true);
    Assertions.assertTrue(decideFluid(tweak, Material.WATER));
    Assertions.assertTrue(decideFluid(tweak, Material.LAVA));
    Assertions.assertFalse(decideFluid(tweak, Material.STONE));
  }

  @Test
  public void fastFluidsSkipsWaterWhenWaterFlagDisabled() throws Exception {
    TweakFastFluids tweak = new TweakFastFluids();
    setFluidFlags(tweak, false, true);
    Assertions.assertFalse(decideFluid(tweak, Material.WATER));
    Assertions.assertTrue(decideFluid(tweak, Material.LAVA));
  }

  @Test
  public void fastFluidsSkipsLavaWhenLavaFlagDisabled() throws Exception {
    TweakFastFluids tweak = new TweakFastFluids();
    setFluidFlags(tweak, true, false);
    Assertions.assertTrue(decideFluid(tweak, Material.WATER));
    Assertions.assertFalse(decideFluid(tweak, Material.LAVA));
  }

  @Test
  public void fastFluidsSkipsAllFluidsWhenBothFlagsDisabled() throws Exception {
    TweakFastFluids tweak = new TweakFastFluids();
    setFluidFlags(tweak, false, false);
    Assertions.assertFalse(decideFluid(tweak, Material.WATER));
    Assertions.assertFalse(decideFluid(tweak, Material.LAVA));
  }

  private static void setFluidFlags(TweakFastFluids tweak, boolean water, boolean lava) throws Exception {
    Field waterField = TweakFastFluids.class.getDeclaredField("accelerateWater");
    waterField.setAccessible(true);
    waterField.setBoolean(tweak, water);
    Field lavaField = TweakFastFluids.class.getDeclaredField("accelerateLava");
    lavaField.setAccessible(true);
    lavaField.setBoolean(tweak, lava);
  }

  private static boolean decideFluid(TweakFastFluids tweak, Material material) throws Exception {
    Method method = TweakFastFluids.class.getDeclaredMethod("isSupportedFluid", Material.class);
    method.setAccessible(true);
    return (boolean) method.invoke(tweak, material);
  }
}
