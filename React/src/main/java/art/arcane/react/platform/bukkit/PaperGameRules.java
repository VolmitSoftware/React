package art.arcane.react.platform.bukkit;

import org.bukkit.GameRule;
import org.bukkit.GameRules;

final class PaperGameRules {
  private PaperGameRules() {
  }

  static GameRule<Integer> randomTickSpeed() {
    return GameRules.RANDOM_TICK_SPEED;
  }
}
