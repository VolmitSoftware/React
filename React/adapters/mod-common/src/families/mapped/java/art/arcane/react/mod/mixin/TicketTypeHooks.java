package art.arcane.react.mod.mixin;

import art.arcane.react.mod.cost.TicketTypeCreators;
import net.minecraft.server.level.TicketType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;

/** Remembers the class that created each ticket type, since types on these versions carry no registry namespace. */
@Mixin(TicketType.class)
public abstract class TicketTypeHooks {
    @Inject(method = "create(Ljava/lang/String;Ljava/util/Comparator;)Lnet/minecraft/server/level/TicketType;", at = @At("RETURN"), require = 0)
    private static void react$created(String name, Comparator<?> comparator, CallbackInfoReturnable<TicketType<?>> callback) {
        TicketTypeCreators.createdByCaller(callback.getReturnValue());
    }

    @Inject(method = "create(Ljava/lang/String;Ljava/util/Comparator;I)Lnet/minecraft/server/level/TicketType;", at = @At("RETURN"), require = 0)
    private static void react$createdTimed(String name, Comparator<?> comparator, int lifespan, CallbackInfoReturnable<TicketType<?>> callback) {
        TicketTypeCreators.createdByCaller(callback.getReturnValue());
    }
}
