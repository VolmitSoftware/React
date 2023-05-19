package art.arcane.edict.content.context;

import art.arcane.edict.api.context.EdictContext;
import art.arcane.edict.api.context.EdictContextResolver;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * The ChunkContext class implements the EdictContextResolver interface for the type {@link Chunk}.
 * It provides functionality to resolve a {@link Chunk} object from the current {@link EdictContext}.
 * <p>
 * This resolution is useful in scenarios where a command executor needs access to the Chunk associated
 * with the player issuing the command, as it provides an easy way to retrieve this Chunk in the context
 * of an Edict command execution.
 */
public class ChunkContext implements EdictContextResolver<Chunk> {

    /**
     * Resolves a Chunk object from the provided EdictContext.
     * <p>
     * If the CommandSender within the context is a Player, the method returns an Optional containing the
     * Chunk where the Player is currently located. If the CommandSender is not a Player, the method returns
     * an empty Optional.
     *
     * @param context the EdictContext from which to resolve the Chunk
     * @return an Optional containing the resolved Chunk if the CommandSender is a Player, or an empty Optional otherwise
     */
    @Override
    public Optional<Chunk> resolve(EdictContext context) {
        return context.getSender() instanceof Player p ? Optional.of(p.getLocation().getChunk()) : Optional.empty();
    }

    /**
     * Provides a convenient way to retrieve the Chunk associated with the current EdictContext.
     * <p>
     * This static method returns an Optional containing the resolved Chunk if the CommandSender within
     * the current EdictContext is a Player, or an empty Optional otherwise.
     *
     * @return an Optional containing the resolved Chunk if the CommandSender within the current EdictContext is a Player, or an empty Optional otherwise
     */
    public static Optional<Chunk> get() {
        return EdictContext.get().resolve(ChunkContext.class);
    }
}
