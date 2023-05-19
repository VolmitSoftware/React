package art.arcane.edict.content.context;

import art.arcane.edict.api.context.EdictContext;
import art.arcane.edict.api.context.EdictContextResolver;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;

public class LocationContext implements EdictContextResolver<Location> {
    @Override
    public Optional<Location> resolve(EdictContext context) {
        return context.getSender() instanceof Player p ? Optional.of(p.getLocation()) : Optional.empty();
    }

    public static Optional<Location> get() {
        return EdictContext.get().resolve(LocationContext.class);
    }
}
