package art.arcane.react.content.tweak;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.entity.StackExclusion;
import org.bukkit.Location;
import org.bukkit.entity.Monster;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class TweakFastEntityIncinerationTest {

  @Test
  public void skipsBurningMonsterWhenPlayerIsNearby() {
    TweakFastEntityIncineration tweak = new TweakFastEntityIncineration();
    EntityDamageEvent event = Mockito.mock(EntityDamageEvent.class);
    Monster monster = Mockito.mock(Monster.class);
    Location location = Mockito.mock(Location.class);
    Mockito.when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FIRE_TICK);
    Mockito.when(event.getEntity()).thenReturn(monster);
    Mockito.when(monster.getLocation()).thenReturn(location);

    try (MockedStatic<StackExclusion> exclusions = Mockito.mockStatic(StackExclusion.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      exclusions.when(() -> StackExclusion.isExcluded(monster)).thenReturn(false);
      react.when(() -> React.hasNearbyPlayer(location, 32D)).thenReturn(true);

      tweak.on(event);

      scheduling.verifyNoInteractions();
    }
  }

  @Test
  public void schedulesBurningMonsterWhenNoPlayerIsNearby() {
    TweakFastEntityIncineration tweak = new TweakFastEntityIncineration();
    EntityDamageEvent event = Mockito.mock(EntityDamageEvent.class);
    Monster monster = Mockito.mock(Monster.class);
    Location location = Mockito.mock(Location.class);
    Mockito.when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FIRE_TICK);
    Mockito.when(event.getEntity()).thenReturn(monster);
    Mockito.when(monster.getLocation()).thenReturn(location);

    try (MockedStatic<StackExclusion> exclusions = Mockito.mockStatic(StackExclusion.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      exclusions.when(() -> StackExclusion.isExcluded(monster)).thenReturn(false);
      react.when(() -> React.hasNearbyPlayer(location, 32D)).thenReturn(false);
      scheduling.when(() -> J.runEntity(Mockito.eq(monster), Mockito.any(Runnable.class), Mockito.anyInt())).thenReturn(true);

      tweak.on(event);

      scheduling.verify(() -> J.runEntity(
          Mockito.eq(monster),
          Mockito.any(Runnable.class),
          Mockito.intThat(delay -> delay >= 0 && delay < 20)
      ));
    }
  }
}
