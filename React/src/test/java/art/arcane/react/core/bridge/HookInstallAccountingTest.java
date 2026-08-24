package art.arcane.react.core.bridge;

import art.arcane.react.content.feature.FeatureLazyGravity;
import art.arcane.react.nms.BrewingTickHook;
import art.arcane.react.nms.ExplosionHook;
import art.arcane.react.nms.ExplosionPacketSuppressor;
import art.arcane.react.nms.FallingBlockTickHook;
import art.arcane.react.nms.FurnaceTickHook;
import art.arcane.react.nms.HopperTickHook;
import art.arcane.react.nms.NmsBridge;
import art.arcane.react.nms.NmsBridges;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

class HookInstallAccountingTest {

  @AfterEach
  void cleanupGlobalBridge() {
    NmsBridges.reset();
  }

  @Test
  void installingEveryHookThenUninstallingEachLeavesZeroInstalled() {
    CountingNmsBridge bridge = new CountingNmsBridge();

    bridge.installFurnaceTickHook(null);
    bridge.installBrewingTickHook(null);
    bridge.installFallingBlockTickHook(null);
    bridge.installExplosionHook(null);
    bridge.installExplosionPacketSuppressor(null);
    bridge.installHopperTickHook(null);

    Assertions.assertEquals(6, bridge.installedCount());
    Assertions.assertEquals(6, bridge.installCalls());

    bridge.uninstallFurnaceTickHook();
    bridge.uninstallBrewingTickHook();
    bridge.uninstallFallingBlockTickHook();
    bridge.uninstallExplosionHook();
    bridge.uninstallExplosionPacketSuppressor();
    bridge.uninstallHopperTickHook();

    Assertions.assertEquals(0, bridge.installedCount());
    Assertions.assertEquals(6, bridge.uninstallCalls());
  }

  @Test
  void redundantUninstallNeverDrivesInstalledCountNegative() {
    CountingNmsBridge bridge = new CountingNmsBridge();

    bridge.installHopperTickHook(null);
    bridge.uninstallHopperTickHook();
    bridge.uninstallHopperTickHook();
    bridge.uninstallHopperTickHook();

    Assertions.assertEquals(0, bridge.installedCount());
    Assertions.assertFalse(bridge.installedCount() < 0);
  }

  @Test
  void reinstallAfterUninstallRestoresHookForReloadCycle() {
    CountingNmsBridge bridge = new CountingNmsBridge();

    Assertions.assertTrue(bridge.installFallingBlockTickHook(null));
    bridge.uninstallFallingBlockTickHook();
    Assertions.assertEquals(0, bridge.installedCount());

    Assertions.assertTrue(bridge.installFallingBlockTickHook(null));
    Assertions.assertEquals(1, bridge.installedCount());
    Assertions.assertTrue(bridge.isInstalled("fallingBlock"));
  }

  @Test
  void nmsBridgesResetUninstallsEveryInstalledHookAndClearsBridge() throws Exception {
    CountingNmsBridge bridge = new CountingNmsBridge();
    bridge.installFurnaceTickHook(null);
    bridge.installBrewingTickHook(null);
    bridge.installFallingBlockTickHook(null);
    bridge.installExplosionHook(null);
    bridge.installExplosionPacketSuppressor(null);
    bridge.installHopperTickHook(null);
    Assertions.assertEquals(6, bridge.installedCount());

    injectActiveBridge(bridge);

    NmsBridges.reset();

    Assertions.assertEquals(0, bridge.installedCount());
    Assertions.assertEquals(6, bridge.uninstallCalls());
    Assertions.assertNull(readStaticBridge());
  }

  @Test
  void featureLazyGravityDeactivateUninstallsItsFallingBlockHook() throws Exception {
    CountingNmsBridge bridge = new CountingNmsBridge();
    injectActiveBridge(bridge);

    FeatureLazyGravity feature = new FeatureLazyGravity();
    feature.onActivate();

    Assertions.assertTrue(bridge.isInstalled("fallingBlock"));
    Assertions.assertTrue(feature.isBridgeActive());
    Assertions.assertEquals(1, bridge.installedCount());

    feature.onDeactivate();

    Assertions.assertFalse(bridge.isInstalled("fallingBlock"));
    Assertions.assertFalse(feature.isBridgeActive());
    Assertions.assertEquals(0, bridge.installedCount());
  }

  @Test
  void everyInstallHookHasAMatchingUninstallHook() {
    Set<String> methodNames = new HashSet<>();
    for (Method method : NmsBridge.class.getMethods()) {
      methodNames.add(method.getName());
    }

    int installMethods = 0;
    for (String name : methodNames) {
      if (!name.startsWith("install")) {
        continue;
      }
      installMethods++;
      String suffix = name.substring("install".length());
      Assertions.assertTrue(methodNames.contains("uninstall" + suffix),
          "Missing uninstall for " + name);
    }

    Assertions.assertEquals(6, installMethods);
  }

  private static void injectActiveBridge(NmsBridge bridge) throws Exception {
    setStaticField("bridge", bridge);
    setStaticField("attempted", Boolean.TRUE);
  }

  private static NmsBridge readStaticBridge() throws Exception {
    Field field = NmsBridges.class.getDeclaredField("bridge");
    field.setAccessible(true);
    return (NmsBridge) field.get(null);
  }

  private static void setStaticField(String name, Object value) throws Exception {
    Field field = NmsBridges.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(null, value);
  }

  private static final class CountingNmsBridge implements NmsBridge {
    private final Set<String> installed = new HashSet<>();
    private int installCalls;
    private int uninstallCalls;

    private boolean install(String key) {
      installCalls++;
      installed.add(key);
      return true;
    }

    private void uninstall(String key) {
      uninstallCalls++;
      installed.remove(key);
    }

    int installedCount() {
      return installed.size();
    }

    int installCalls() {
      return installCalls;
    }

    int uninstallCalls() {
      return uninstallCalls;
    }

    boolean isInstalled(String key) {
      return installed.contains(key);
    }

    @Override
    public String version() {
      return "counting-fake";
    }

    @Override
    public boolean installFurnaceTickHook(FurnaceTickHook hook) {
      return install("furnace");
    }

    @Override
    public boolean installBrewingTickHook(BrewingTickHook hook) {
      return install("brewing");
    }

    @Override
    public boolean installFallingBlockTickHook(FallingBlockTickHook hook) {
      return install("fallingBlock");
    }

    @Override
    public boolean installExplosionHook(ExplosionHook hook) {
      return install("explosion");
    }

    @Override
    public boolean installExplosionPacketSuppressor(ExplosionPacketSuppressor suppressor) {
      return install("explosionSuppressor");
    }

    @Override
    public boolean installHopperTickHook(HopperTickHook hook) {
      return install("hopper");
    }

    @Override
    public void uninstallFurnaceTickHook() {
      uninstall("furnace");
    }

    @Override
    public void uninstallBrewingTickHook() {
      uninstall("brewing");
    }

    @Override
    public void uninstallFallingBlockTickHook() {
      uninstall("fallingBlock");
    }

    @Override
    public void uninstallExplosionHook() {
      uninstall("explosion");
    }

    @Override
    public void uninstallExplosionPacketSuppressor() {
      uninstall("explosionSuppressor");
    }

    @Override
    public void uninstallHopperTickHook() {
      uninstall("hopper");
    }

    @Override
    public boolean supportsFurnaceTickHook() {
      return true;
    }

    @Override
    public boolean supportsBrewingTickHook() {
      return true;
    }

    @Override
    public boolean supportsFallingBlockTickHook() {
      return true;
    }

    @Override
    public boolean supportsExplosionHook() {
      return true;
    }

    @Override
    public boolean supportsExplosionPacketSuppressor() {
      return true;
    }

    @Override
    public boolean supportsHopperTickHook() {
      return true;
    }

    @Override
    public boolean broadcastMergedExplosion(World world, double x, double y, double z, float radius, double rangeBlocks) {
      return true;
    }
  }
}
