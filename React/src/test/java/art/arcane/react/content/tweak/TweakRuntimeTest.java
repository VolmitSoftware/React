package art.arcane.react.content.tweak;

import org.bukkit.Material;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TweakRuntimeTest {

  @Test
  public void fastFluidsAcceleratesWaterAndLavaByDefault() {
    TweakFastFluids tweak = new TweakFastFluids();
    Assertions.assertTrue(tweak.isAccelerateWater());
    Assertions.assertTrue(tweak.isAccelerateLava());
  }

  @Test
  public void fastFluidsAcceleratesBothFluidsWhenBothFlagsEnabled() throws Exception {
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

  @Test
  public void hopperIdleStretchEnabledByDefault() throws Exception {
    TweakHopperIndex tweak = new TweakHopperIndex();
    Assertions.assertTrue(readBooleanField(tweak, "idleStretch"));
  }

  @Test
  public void hopperIdleStretchTicksExceedHopperCooldownSoStretchEngages() throws Exception {
    TweakHopperIndex tweak = new TweakHopperIndex();
    int idleStretchTicks = readIntField(tweak, "idleStretchTicks");
    int hopperCooldownTicks = readStaticIntField(TweakHopperIndex.class, "HOPPER_COOLDOWN_TICKS");
    Assertions.assertTrue(idleStretchTicks > hopperCooldownTicks);
  }

  @Test
  public void hopperIdleStretchSpreadPassesIsAtLeastOne() throws Exception {
    TweakHopperIndex tweak = new TweakHopperIndex();
    Assertions.assertTrue(readIntField(tweak, "idleStretchSpreadPasses") >= 1);
  }

  @Test
  public void hopperIdleStretchMinTickMsIsPositive() throws Exception {
    TweakHopperIndex tweak = new TweakHopperIndex();
    Assertions.assertTrue(readDoubleField(tweak, "idleStretchMinTickMs") > 0.0D);
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

  private static boolean readBooleanField(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.getBoolean(target);
  }

  private static int readIntField(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.getInt(target);
  }

  private static double readDoubleField(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.getDouble(target);
  }

  private static int readStaticIntField(Class<?> type, String name) throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    return field.getInt(null);
  }
}
