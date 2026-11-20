package art.arcane.react.content.tweak;

import art.arcane.react.platform.EntityEventHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ItemHost;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.TestGameEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class ItemTweaksCoreTest {
  private static final GameLocation SPOT = new GameLocation(UUID.randomUUID(), 0.5D, 64D, 0.5D);

  @Test
  void orbMergeMovesExperienceIntoTheSpawnedOrbUpToTheCap() throws Exception {
    TweakExperienceOrbMerge tweak = new TweakExperienceOrbMerge();
    set(tweak, "maxExperiencePerOrb", 20);
    ItemHost host = Mockito.mock(ItemHost.class);
    set(tweak, "items", host);
    TestGameEntity collector = new TestGameEntity("EXPERIENCE_ORB", SPOT);
    TestGameEntity full = new TestGameEntity("EXPERIENCE_ORB", SPOT);
    TestGameEntity partial = new TestGameEntity("EXPERIENCE_ORB", SPOT);
    Map<TestGameEntity, Integer> experience = new HashMap<>();
    experience.put(collector, 5);
    experience.put(full, 7);
    experience.put(partial, 11);
    Mockito.when(host.experience(Mockito.any())).thenAnswer(invocation -> experience.get(invocation.getArgument(0)));
    Mockito.doAnswer(invocation -> experience.put(invocation.getArgument(0), invocation.getArgument(1)))
        .when(host).setExperience(Mockito.any(), Mockito.anyInt());
    Mockito.when(host.nearbyOrbs(collector, 2.75D)).thenReturn(List.of(full, partial));

    tweak.on(collector);

    Assertions.assertEquals(20, experience.get(collector));
    Assertions.assertEquals(3, experience.get(partial));
    Mockito.verify(host).discard(full);
    Mockito.verify(host, Mockito.never()).discard(partial);
  }

  @Test
  void projectileLimiterCapsEachChunkWindow() throws Exception {
    TweakProjectileLimiter tweak = new TweakProjectileLimiter();
    set(tweak, "maxProjectilesPerChunkWindow", 2);
    set(tweak, "chunkWindowMS", 60_000);
    tweak.onActivate();
    int cancelled = 0;
    for (int shot = 0; shot < 5; shot++) {
      EntityEventHost.Launch launch = launch(Optional.empty());
      tweak.on(launch);
      if (Mockito.mockingDetails(launch).getInvocations().stream()
          .anyMatch(invocation -> invocation.getMethod().getName().equals("cancel"))) {
        cancelled++;
      }
    }
    tweak.onDeactivate();

    Assertions.assertEquals(3, cancelled);
  }

  @Test
  void projectileLimiterLetsBypassingPlayersThroughThePlayerWindow() throws Exception {
    TweakProjectileLimiter tweak = new TweakProjectileLimiter();
    set(tweak, "maxProjectilesPerPlayerWindow", 1);
    set(tweak, "limitChunkProjectiles", false);
    ReactPlayer limited = player(false);
    ReactPlayer bypassing = player(true);
    tweak.onActivate();

    EntityEventHost.Launch first = launch(Optional.of(limited));
    EntityEventHost.Launch second = launch(Optional.of(limited));
    EntityEventHost.Launch bypassed = launch(Optional.of(bypassing));
    EntityEventHost.Launch bypassedAgain = launch(Optional.of(bypassing));
    tweak.on(first);
    tweak.on(second);
    tweak.on(bypassed);
    tweak.on(bypassedAgain);
    tweak.onDeactivate();

    Mockito.verify(first, Mockito.never()).cancel();
    Mockito.verify(second).cancel();
    Mockito.verify(bypassedAgain, Mockito.never()).cancel();
  }

  @Test
  void despawnAcceleratorAgesOrdinaryDropsAwayFromPlayers() throws Exception {
    TweakItemDespawnAccelerator tweak = new TweakItemDespawnAccelerator();
    ItemHost items = Mockito.mock(ItemHost.class);
    EntityEventHost events = Mockito.mock(EntityEventHost.class);
    set(tweak, "items", items);
    set(tweak, "events", events);
    TestGameEntity cobble = new TestGameEntity("ITEM", SPOT).ticksLived(40);
    TestGameEntity diamond = new TestGameEntity("ITEM", SPOT);
    TestGameEntity named = new TestGameEntity("ITEM", SPOT).named();
    TestGameEntity watched = new TestGameEntity("ITEM", SPOT);
    Mockito.when(items.material(cobble)).thenReturn("COBBLESTONE");
    Mockito.when(items.material(diamond)).thenReturn("DIAMOND");
    Mockito.when(items.material(watched)).thenReturn("COBBLESTONE");
    Mockito.when(events.nearPlayer(watched, 48D)).thenReturn(true);

    tweak.on(cobble);
    tweak.on(diamond);
    tweak.on(named);
    tweak.on(watched);

    Mockito.verify(items).setTicksLived(cobble, 5600);
    Mockito.verify(items, Mockito.never()).setTicksLived(Mockito.eq(diamond), Mockito.anyInt());
    Mockito.verify(items, Mockito.never()).setTicksLived(Mockito.eq(named), Mockito.anyInt());
    Mockito.verify(items, Mockito.never()).setTicksLived(Mockito.eq(watched), Mockito.anyInt());
  }

  private static EntityEventHost.Launch launch(Optional<ReactPlayer> shooter) {
    EntityEventHost.Launch launch = Mockito.mock(EntityEventHost.Launch.class);
    Mockito.when(launch.projectile()).thenReturn(new TestGameEntity("ARROW", SPOT));
    Mockito.when(launch.shooter()).thenReturn(shooter);
    return launch;
  }

  private static ReactPlayer player(boolean bypass) {
    ReactPlayer player = Mockito.mock(ReactPlayer.class);
    Mockito.when(player.id()).thenReturn(UUID.randomUUID());
    Mockito.when(player.hasPermission("react.bypass.projectile-limit")).thenReturn(bypass);
    return player;
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
