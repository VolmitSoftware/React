package art.arcane.react.content.feature;

import art.arcane.react.core.integration.GlossEntityOverlayIntegration;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.bukkit.BukkitGameEntity;
import art.arcane.react.platform.bukkit.BukkitMobStackHost;
import art.arcane.react.platform.bukkit.MobStateAccess;
import org.bukkit.entity.Entity;
import org.mockito.Mockito;

final class MobStacks {
  private MobStacks() {
  }

  static GameEntity entity(Entity entity) {
    return new BukkitGameEntity(entity);
  }

  static FeatureMobStacking feature(GlossEntityOverlayIntegration overlays, MobStateAccess state) {
    return new FeatureMobStacking(new BukkitMobStackHost(overlays, state));
  }

  static BukkitMobStackHost events(FeatureMobStacking feature) {
    BukkitMobStackHost host = new BukkitMobStackHost(Mockito.mock(GlossEntityOverlayIntegration.class), MobStateAccess.detect());
    host.listen(feature);
    return host;
  }
}
