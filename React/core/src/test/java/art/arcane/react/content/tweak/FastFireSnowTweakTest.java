package art.arcane.react.content.tweak;

import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.GrowthHost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class FastFireSnowTweakTest {
  @Test
  void fastSnowTakesOverSnowLayerFormAndFadeOnly() {
    TweakFastSnow tweak = new TweakFastSnow();
    Change snow = new Change("SNOW");
    Change ice = new Change("ICE");

    tweak.onForm(snow);
    tweak.onForm(ice);
    Assertions.assertTrue(snow.isCancelled());
    Assertions.assertFalse(ice.isCancelled());

    Change melting = new Change("SNOW");
    Change block = new Change("SNOW_BLOCK");
    tweak.onFade(melting);
    tweak.onFade(block);
    Assertions.assertTrue(melting.isCancelled());
    Assertions.assertFalse(block.isCancelled());
  }

  @Test
  void fastFireTakesOverFireSpreadAndFadeAndEveryBurn() {
    TweakFastFire tweak = new TweakFastFire();
    Change fire = new Change("FIRE");
    Change soulFire = new Change("SOUL_FIRE");
    tweak.onSpread(fire);
    tweak.onSpread(soulFire);
    Assertions.assertTrue(fire.isCancelled());
    Assertions.assertFalse(soulFire.isCancelled());

    Change fading = new Change("FIRE");
    Change grass = new Change("GRASS_BLOCK");
    tweak.onFade(fading);
    tweak.onFade(grass);
    Assertions.assertTrue(fading.isCancelled());
    Assertions.assertFalse(grass.isCancelled());

    Change planks = new Change("OAK_PLANKS");
    tweak.onBurn(planks);
    Assertions.assertTrue(planks.isCancelled());
  }

  private static final class Change implements GrowthHost.SpreadEvent {
    private final String type;
    private boolean cancelled;

    private Change(String type) {
      this.type = type;
    }

    @Override
    public GameLocation location() {
      return new GameLocation(UUID.randomUUID(), 1, 64, 1);
    }

    @Override
    public String type() {
      return type;
    }

    @Override
    public Object state() {
      return type;
    }

    @Override
    public GameLocation source() {
      return location();
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }
  }
}
