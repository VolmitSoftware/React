package art.arcane.react.util.director.context;

import art.arcane.react.util.director.DirectorContextHandler;
import art.arcane.react.util.plugin.VolmitSender;
import org.bukkit.entity.Player;

public final class PlayerContextHandler implements DirectorContextHandler<Player> {
  @Override
  public Class<Player> getType() {
    return Player.class;
  }

  @Override
  public Player handle(VolmitSender sender) {
    return sender.isPlayer() ? sender.player() : null;
  }
}
