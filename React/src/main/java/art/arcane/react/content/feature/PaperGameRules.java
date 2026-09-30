package art.arcane.react.content.feature;

import org.bukkit.GameRule;
import org.bukkit.GameRules;

final class PaperGameRules {
  private PaperGameRules() {
  }

  static GameRule<Integer> randomTickSpeed() {
    return GameRules.RANDOM_TICK_SPEED;
  }
}
