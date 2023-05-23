package art.arcane.edict.api.context;

import art.arcane.edict.content.context.ChunkContext;
import art.arcane.edict.content.context.LocationContext;
import art.arcane.edict.content.context.TargetBlockContext;
import art.arcane.edict.content.context.WorldContext;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * A helper interface. This is optional but it makes accessing context values easier
 */
public interface EdictContextual {
    /**
     * Get the context
     *
     * @return the context
     */
    default EdictContext context(){
        return EdictContext.get();
    }

    default Block targetBlock() {
        return TargetBlockContext.get().orElse(null);
    }

    /**
     * Get the player who sent the command
     * @return the player or null if its a server command
     */
    default Player player() {
        return context().getPlayer();
    }

    default Optional<Player> optionalPlayer() {
        return player() == null ? Optional.empty() : Optional.of(player());
    }

    /**
     * Get the sender who sent the command
     * @return the sender
     */
    default CommandSender sender() {
        return context().getSender();
    }

    /**
     * Get the world of the player who sent the command
     * @return the world or null if its a server command
     */
    default World world() {
        return WorldContext.get().orElse(null);
    }

    /**
     * Get the location of the player who sent the command
     * @return the location or null if its a server command
     */
    default Location location() {
        return LocationContext.get().orElse(null);
    }

    /**
     * Get the chunk of the player who sent the command
     * @return the chunk or null if its a server command
     */
    default Chunk chunk() {
        return ChunkContext.get().orElse(null);
    }
}
