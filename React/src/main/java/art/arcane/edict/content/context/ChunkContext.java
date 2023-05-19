package art.arcane.edict.content.context;

import art.arcane.edict.api.context.EdictContext;
import art.arcane.edict.api.context.EdictContextResolver;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.Optional;

public class ChunkContext implements EdictContextResolver<Chunk> {
    @Override
    public Optional<Chunk> resolve(EdictContext context) {
        return context.getSender() instanceof Player p ? Optional.of(p.getLocation().getChunk()) : Optional.empty();
    }

    public static Optional<Chunk> get() {
        return EdictContext.get().resolve(ChunkContext.class);
    }
}
