package art.arcane.react.mod.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(TicketStorage.class)
public interface TicketStorageAccess {
    @Accessor("tickets")
    Long2ObjectOpenHashMap<List<Ticket>> react$tickets();
}
