package art.arcane.edict.content.context;

import art.arcane.edict.api.context.EdictContext;
import art.arcane.edict.api.context.EdictContextResolver;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Optional;

public class WorldContext implements EdictContextResolver<World> {
    @Override
    public Optional<World> resolve(EdictContext context) {
        return context.getSender() instanceof Player p ? Optional.of(p.getWorld()) : Optional.empty();
    }

    public static Optional<World> get() {
        return EdictContext.get().resolve(WorldContext.class);
    }
}
