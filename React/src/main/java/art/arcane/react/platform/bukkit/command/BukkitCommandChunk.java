package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.content.directorcommand.ChunkCommands;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.platform.bukkit.BukkitCommandSender;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.scheduling.EntityTeleports;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class BukkitCommandChunk {
  public void sample(VolmitSender sender) {
    SampledChunk c = React.controller(ObserverController.class).getSampled().getChunk(BukkitCommandSender.player(sender).getLocation().getChunk());

    if (c != null) {
      ChunkCommands.sendSamples(sender, c);
    } else {
      ReactLanguage.sendPrefixed(sender, CommandMessages.CHUNK_NOT_SAMPLED);
    }
  }

  public void worst(VolmitSender sender) {
    SampledChunk c = React.instance.controller(ObserverController.class).absoluteWorst();

    if (c != null) {
      World world = Bukkit.getWorld(c.getWorldId());
      if (world == null) {
        ReactLanguage.sendPrefixed(sender, CommandMessages.NO_CHUNKS_SAMPLED);
        return;
      }

      int chunkX = c.getChunkX();
      int chunkZ = c.getChunkZ();
      int blockX = (chunkX << 4) + 8;
      int blockZ = (chunkZ << 4) + 8;
      Player p = BukkitCommandSender.player(sender);
      if (!J.runChunk(world, chunkX, chunkZ, () -> {
        Location destination = world.getHighestBlockAt(blockX, blockZ).getLocation().add(0.5D, 1D, 0.5D);
        EntityTeleports.teleport(React.instance, p, destination, PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((teleported, failure) -> {
          if (failure != null) {
            React.reportError(new IllegalStateException("Failed to teleport to worst sampled chunk", failure));
          }
        });
      })) {
        React.reportError(new IllegalStateException("Failed to schedule worst sampled chunk destination lookup"));
      }

      ChunkCommands.sendSamples(sender, c);
    } else {
      ReactLanguage.sendPrefixed(sender, CommandMessages.NO_CHUNKS_SAMPLED);
    }
  }
}
