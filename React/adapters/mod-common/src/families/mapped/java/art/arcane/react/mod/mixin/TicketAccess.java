package art.arcane.react.mod.mixin;

import net.minecraft.server.level.Ticket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Ticket.class)
public interface TicketAccess {
    @Accessor("key")
    Object react$key();
}
