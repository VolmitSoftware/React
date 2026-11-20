package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.content.action.ActionHopperNetworkNormalize;
import art.arcane.react.content.action.ActionPurgeEntities;
import art.arcane.react.content.action.ActionTrimEntitiesByAgePriority;
import art.arcane.react.platform.bukkit.BukkitCommandSender;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.react.util.director.handlers.OptionalWorldHandler;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;


public final class BukkitCommandAction {
  public void purgeEntities(VolmitSender sender, int radius, String world) {
    Action<ActionPurgeEntities.Params> pe = React.action("purge-entities");
    ActionPurgeEntities.Params p = pe.getDefaultParams();

    if (!world.equals("ALL")) {
      p.withWorld(WorldIdentity.resolve(world).orElseThrow());
    }

    if (sender.isPlayer()) {
      if (radius > 0) {
        Chunk c = BukkitCommandSender.player(sender).getLocation().getChunk();
        p.addRadius(c.getWorld(), c.getX(), c.getZ(), Math.min(radius, 10));
      }
    }

    pe.create(p, BukkitCommandSender.bukkit(sender)).queue();
  }

  public void trimEntitiesByAgePriority(VolmitSender sender, int maxEntities, int minAgeSeconds, String world) {
    Action<ActionTrimEntitiesByAgePriority.Params> action = React.action(ActionTrimEntitiesByAgePriority.ID);
    ActionTrimEntitiesByAgePriority.Params p = action.getDefaultParams()
        .setMaxTrim(Math.max(1, Math.min(maxEntities, 10_000)))
        .setMinEntityAgeTicks(Math.max(1, minAgeSeconds) * 20);

    if (!world.equals("ALL")) {
      p.withWorld(WorldIdentity.resolve(world).orElseThrow());
    }

    action.create(p, BukkitCommandSender.bukkit(sender)).queue();
  }

  public void hopperNetworkNormalize(VolmitSender sender, int maxChunks, double minHopperUpdates, String world) {
    Action<ActionHopperNetworkNormalize.Params> action = React.action(ActionHopperNetworkNormalize.ID);
    ActionHopperNetworkNormalize.Params p = action.getDefaultParams()
        .setMaxChunks(Math.max(1, Math.min(maxChunks, 256)))
        .setMinimumHopperUpdatesPerChunk(Math.max(1D, minHopperUpdates));

    if (!world.equals("ALL")) {
      p.withWorld(WorldIdentity.resolve(world).orElseThrow());
    }

    action.create(p, BukkitCommandSender.bukkit(sender)).queue();
  }

}
