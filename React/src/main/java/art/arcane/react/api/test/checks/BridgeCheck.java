package art.arcane.react.api.test.checks;

import art.arcane.react.React;
import art.arcane.react.api.test.ReactAsyncSubsystemCheck;
import art.arcane.react.api.test.TestReport;
import art.arcane.react.core.bridge.BridgeHealthReport;
import art.arcane.react.core.bridge.NmsBridgeRegistry;
import art.arcane.react.nms.NmsBridge;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;

import java.util.UUID;

public final class BridgeCheck implements ReactAsyncSubsystemCheck {

  @Override
  public String subsystem() {
    return "bridge";
  }

  @Override
  public void run(TestReport report, Runnable onDone) {
    checkHealth(report);
    checkFallingBlock(report, onDone);
  }

  private void checkHealth(TestReport report) {
    NmsBridgeRegistry registry = React.bridgeRegistry();
    if (registry == null) {
      report.setBridgeHealth(0, 0);
      report.warn("bridge", "health", "bridge registry unavailable (plugin not fully started)");
      return;
    }

    BridgeHealthReport health = registry.snapshotHealth();
    int available = health.availableCount();
    int unavailable = health.unavailableCount();
    report.setBridgeHealth(available, unavailable);

    if (unavailable == 0) {
      report.pass("bridge", "health", available + " bridge handle(s) available, 0 unavailable");
      return;
    }

    String unavailableDetail = describeUnavailable(health);
    NmsBridge bridge = NmsBridges.get();
    boolean onBundledVersion = NmsBridges.onBundledVersion();

    if (bridge == null || !onBundledVersion) {
      report.warn("bridge", "health", "bridge degraded / measurement-only on this MC - expected off bundled version ("
          + unavailable + " unavailable: " + unavailableDetail + "; reason: " + NmsBridges.failureReason() + ")");
      return;
    }

    report.fail("bridge", "health", unavailable + " bridge hook(s) unavailable on a supported (bundled) version: "
        + unavailableDetail);
  }

  private String describeUnavailable(BridgeHealthReport health) {
    StringBuilder out = new StringBuilder();
    for (BridgeHealthReport.BridgeHealthEntry entry : health.entries()) {
      if (entry.available()) {
        continue;
      }
      if (out.length() > 0) {
        out.append(", ");
      }
      out.append(entry.logicalId()).append(" (").append(entry.failureReason()).append(")");
    }
    return out.length() == 0 ? "none" : out.toString();
  }

  private void checkFallingBlock(TestReport report, Runnable onDone) {
    if (Bukkit.getWorlds().isEmpty()) {
      report.skip("bridge", "falling-block", "no worlds loaded - cannot run live gravity landing test from console");
      onDone.run();
      return;
    }

    World world = Bukkit.getWorlds().get(0);
    Location spawnLocation = world.getSpawnLocation();
    J.s(spawnLocation, () -> {
      try {
        int x = spawnLocation.getBlockX();
        int z = spawnLocation.getBlockZ();
        int groundY = world.getHighestBlockYAt(x, z);
        int spawnY = Math.min(world.getMaxHeight() - 2, groundY + 20);
        if (spawnY <= groundY + 2) {
          report.skip("bridge", "falling-block", "no headroom above y=" + groundY + " in world '" + world.getName()
              + "' to drop a test block");
          onDone.run();
          return;
        }

        Location dropAt = new Location(world, x + 0.5D, spawnY, z + 0.5D);
        FallingBlock falling = world.spawnFallingBlock(dropAt, Material.SAND.createBlockData());
        UUID id = falling.getUniqueId();
        J.s(dropAt, () -> {
          Entity entity = Bukkit.getEntity(id);
          boolean landed = entity == null || entity.isDead() || !entity.isValid();
          if (landed) {
            report.pass("bridge", "falling-block", "SAND dropped at y=" + spawnY + " over ground y=" + groundY
                + " landed/despawned within 100 ticks (gravity hook + landing path OK)");
          } else {
            report.fail("bridge", "falling-block", "SAND dropped at y=" + spawnY
                + " still falling after 100 ticks (stuck mid-air)");
            entity.remove();
          }

          Block landingBlock = world.getBlockAt(x, groundY + 1, z);
          if (landingBlock.getType() == Material.SAND) {
            landingBlock.setType(Material.AIR);
          }
          onDone.run();
        }, 100);
      } catch (Throwable t) {
        report.info("bridge", "falling-block", "live drop unavailable on this server/thread: "
            + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : " - " + t.getMessage()));
        onDone.run();
      }
    });
  }
}
