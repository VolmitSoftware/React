package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.integration.RemoteSamplerBridge;
import art.arcane.react.platform.IntegrationMetricsHost;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Integration metrics from the providers {@link IntegrationController} discovers through the services manager.
 */
public final class BukkitIntegrationMetricsHost implements IntegrationMetricsHost {
  public static final BukkitIntegrationMetricsHost INSTANCE = new BukkitIntegrationMetricsHost();

  private BukkitIntegrationMetricsHost() {
  }

  @Override
  public boolean available() {
    return bridge() != null;
  }

  @Override
  public Status status(String integrationId) {
    IntegrationController controller = React.controller(IntegrationController.class);
    IntegrationController.IntegrationStatus status = controller == null ? null : controller.statusFor(integrationId);
    return status == null ? null : new Status(status.health().name(), status.message());
  }

  @Override
  public IntegrationMetricSample sample(String integrationId, String key) {
    RemoteSamplerBridge bridge = bridge();
    return bridge == null ? null : bridge.getSample(integrationId, key);
  }

  @Override
  public IntegrationMetricSample storedSample(String integrationId, String key) {
    RemoteSamplerBridge bridge = bridge();
    return bridge == null ? null : bridge.storedSample(integrationId, key);
  }

  @Override
  public Map<String, IntegrationMetricSample> samples(String integrationId) {
    RemoteSamplerBridge bridge = bridge();
    return bridge == null ? Map.of() : bridge.snapshot(integrationId);
  }

  @Override
  public List<IntegrationMetricGroup> groups(String integrationId, String scopeKind) {
    RemoteSamplerBridge bridge = bridge();
    return bridge == null ? List.of() : bridge.groups(integrationId, scopeKind);
  }

  @Override
  public IntegrationMetricGroup group(String integrationId, String scopeKind, String scopeId) {
    RemoteSamplerBridge bridge = bridge();
    return bridge == null ? null : bridge.getGroup(integrationId, scopeKind, scopeId);
  }

  @Override
  public String worldScope(MapRenderTarget target) {
    World world = BukkitMapTargets.world(target);
    Player viewer = world == null ? BukkitMapTargets.player(target) : null;
    if (viewer != null) {
      world = viewer.getWorld();
    }
    return world == null ? null : WorldIdentity.serialize(world);
  }

  private static RemoteSamplerBridge bridge() {
    IntegrationController controller = React.controller(IntegrationController.class);
    return controller == null ? null : controller.getRemoteSamplerBridge();
  }
}
