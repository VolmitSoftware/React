package art.arcane.edict.content.context;

import art.arcane.edict.api.context.EdictContext;
import art.arcane.edict.api.context.EdictContextResolver;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Optional;

public class TargetBlockContext implements EdictContextResolver<Block> {
    public static Optional<Block> get() {
        return EdictContext.get().resolve(TargetBlockContext.class);
    }

    @Override
    public Optional<Block> resolve(EdictContext context) {
        return context.getSender() instanceof Player p ? Optional.of(p.getTargetBlockExact(5)) : Optional.empty();
    }
}
