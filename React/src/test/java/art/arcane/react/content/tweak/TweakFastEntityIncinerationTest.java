package art.arcane.react.content.tweak;

import art.arcane.react.React;
import art.arcane.react.api.protect.internal.ProtectionInstaller;
import art.arcane.react.util.common.scheduling.J;
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

    ProtectionInstaller.install(null);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
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

    ProtectionInstaller.install(null);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
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
