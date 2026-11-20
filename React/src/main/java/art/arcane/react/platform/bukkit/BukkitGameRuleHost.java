package art.arcane.react.platform.bukkit;

import art.arcane.react.platform.GameRuleHost;
import art.arcane.react.platform.ReactWorld;
import org.bukkit.GameRule;
import org.bukkit.World;

public final class BukkitGameRuleHost implements GameRuleHost {
  // GameRules constants are paper-only; the legacy GameRule constant survives on spigot
  private static volatile GameRule<Integer> randomTickSpeedRule;

  @SuppressWarnings("removal") // legacy constant only resolved where paper GameRules is absent
  public static GameRule<Integer> randomTickSpeedRule() {
    GameRule<Integer> rule = randomTickSpeedRule;
    if (rule == null) {
      try {
        rule = PaperGameRules.randomTickSpeed();
      } catch (NoClassDefFoundError e) {
        rule = GameRule.RANDOM_TICK_SPEED;
      }
      randomTickSpeedRule = rule;
    }
    return rule;
  }

  @Override
  public Integer randomTickSpeed(ReactWorld world) {
    return ((World) world.handle()).getGameRuleValue(randomTickSpeedRule());
  }

  @Override
  public boolean setRandomTickSpeed(ReactWorld world, int value) {
    return ((World) world.handle()).setGameRule(randomTickSpeedRule(), value);
  }
}
