package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.util.project.world.EntityKiller;
import art.arcane.volmlib.util.event.ProtectionProbe;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProtectionProbeActivityTest {
  @Test
  void previewDoesNotConsumeTheInteractionBudget() throws Exception {
    FeatureAdaptRuntimeSurgeGuard feature = new FeatureAdaptRuntimeSurgeGuard();
    setField(feature, "surge", true);
    feature.on(probe(player()));

    Map<?, ?> counters = (Map<?, ?>) field(feature, "interactionOps");
    assertTrue(counters.isEmpty());
  }

  @Test
  void previewDoesNotRefreshIdleActivity() throws Exception {
    FeatureAfkViewShedding feature = new FeatureAfkViewShedding();
    Player player = player();
    Map<UUID, Long> activity = new ConcurrentHashMap<>();
    activity.put(player.getUniqueId(), 1L);
    setField(feature, "activeGeneration", 1L);
    setField(feature, "lastActivityMs", activity);

    feature.on(probe(player));

    assertEquals(1L, activity.get(player.getUniqueId()));
  }

  @Test
  @SuppressWarnings("unchecked")
  void entityPreviewDoesNotCancelRemovalCountdown() throws Exception {
    Field activeField = EntityKiller.class.getDeclaredField("ACTIVE");
    activeField.setAccessible(true);
    Map<UUID, EntityKiller> active = (Map<UUID, EntityKiller>) activeField.get(null);
    Entity entity = mock(Entity.class);
    EntityKiller killer = mock(EntityKiller.class);
    UUID entityId = UUID.randomUUID();
    when(entity.getUniqueId()).thenReturn(entityId);
    active.put(entityId, killer);
    try {
      new EntityKiller.SharedListener().on(ProtectionProbe.entityInteract(player(), entity));
      verify(killer, never()).stop();
    } finally {
      active.remove(entityId);
    }
  }

  @Test
  void inventoryEntityPreviewDoesNotSplitConfiguredMobStacks() {
    FeatureMobStacking feature = mock(FeatureMobStacking.class);
    Player player = player();
    LivingEntity horse = mock(LivingEntity.class);
    LivingEntity replacement = mock(LivingEntity.class);
    World world = mock(World.class);
    when(player.isSneaking()).thenReturn(true);
    when(horse.getType()).thenReturn(EntityType.HORSE);
    when(horse.getWorld()).thenReturn(world);
    when(horse.getLocation()).thenReturn(new Location(world, 0, 64, 0));
    when(world.spawnEntity(any(Location.class), any(EntityType.class))).thenReturn(replacement);
    when(feature.isStackableType(EntityType.HORSE)).thenReturn(true);
    when(feature.getStackCount(horse)).thenReturn(4);
    doCallRealMethod().when(feature).onPlayerInteractEntity(any());

    try (MockedStatic<React> react = mockStatic(React.class)) {
      feature.onPlayerInteractEntity(ProtectionProbe.entityInteract(player, horse));
    }

    verify(world, never()).spawnEntity(any(Location.class), any(EntityType.class));
  }

  private static Player player() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
    return player;
  }

  private static PlayerInteractEvent probe(Player player) {
    return ProtectionProbe.blockInteract(player, mock(Block.class), EquipmentSlot.HAND);
  }

  private static Object field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
