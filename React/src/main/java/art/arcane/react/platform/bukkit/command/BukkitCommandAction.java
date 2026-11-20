package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.content.action.ActionHopperNetworkNormalize;
import art.arcane.react.platform.bukkit.BukkitCommandSender;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.react.util.director.handlers.OptionalWorldHandler;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;


public final class BukkitCommandAction {
  public void hopperNetworkNormalize(VolmitSender sender, int maxChunks, double minHopperUpdates, String world) {
    Action<ActionHopperNetworkNormalize.Params> action = React.action(ActionHopperNetworkNormalize.ID);
    ActionHopperNetworkNormalize.Params p = action.getDefaultParams()
        .setMaxChunks(Math.max(1, Math.min(maxChunks, 256)))
        .setMinimumHopperUpdatesPerChunk(Math.max(1D, minHopperUpdates));

    if (!world.equals("ALL")) {
      p.withWorld(WorldIdentity.serialize(WorldIdentity.resolve(world).orElseThrow()));
    }

    action.create(p, BukkitCommandSender.bukkit(sender)).queue();
  }
}
