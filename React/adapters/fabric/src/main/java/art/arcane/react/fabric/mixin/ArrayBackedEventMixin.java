package art.arcane.react.fabric.mixin;

import art.arcane.react.fabric.cost.FabricEventWrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.fabricmc.fabric.impl.base.event.ArrayBackedEvent", remap = false)
abstract class ArrayBackedEventMixin {
    @ModifyArg(method = "update()V", require = 0, at = @At(value = "INVOKE",
            target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object react$timeHandlers(Object handlers) {
        return FabricEventWrap.active ? FabricEventWrap.wrapArray(handlers) : handlers;
    }
}
