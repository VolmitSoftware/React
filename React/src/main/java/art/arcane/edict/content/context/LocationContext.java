package art.arcane.edict.content.context;

import art.arcane.edict.api.context.EdictContext;
import art.arcane.edict.api.context.EdictContextResolver;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * The LocationContext class implements the EdictContextResolver interface for the type {@link Location}.
 * It provides functionality to resolve a {@link Location} object from the current {@link EdictContext}.
 * <p>
 * This resolution is useful in scenarios where a command executor needs access to the Location associated
 * with the player issuing the command, as it provides an easy way to retrieve this Location in the context
 * of an Edict command execution.
 */
public class LocationContext implements EdictContextResolver<Location> {

    /**
     * Resolves a Location object from the provided EdictContext.
     * <p>
     * If the CommandSender within the context is a Player, the method returns an Optional containing the
     * Location where the Player is currently located. If the CommandSender is not a Player, the method returns
     * an empty Optional.
     *
     * @param context the EdictContext from which to resolve the Location
     * @return an Optional containing the resolved Location if the CommandSender is a Player, or an empty Optional otherwise
     */
    @Override
    public Optional<Location> resolve(EdictContext context) {
        return context.getSender() instanceof Player p ? Optional.of(p.getLocation()) : Optional.empty();
    }

    /**
     * Provides a convenient way to retrieve the Location associated with the current EdictContext.
     * <p>
     * This static method returns an Optional containing the resolved Location if the CommandSender within
     * the current EdictContext is a Player, or an empty Optional otherwise.
     *
     * @return an Optional containing the resolved Location if the CommandSender within the current EdictContext is a Player, or an empty Optional otherwise
     */
    public static Optional<Location> get() {
        return EdictContext.get().resolve(LocationContext.class);
    }
}
