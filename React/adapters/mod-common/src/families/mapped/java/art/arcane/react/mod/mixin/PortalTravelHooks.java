package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPortalHost;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Portal travel, where Bukkit fires its player and entity portal events once the destination is known. */
@Mixin(Entity.class)
public abstract class PortalTravelHooks {
    @WrapOperation(method = "handlePortal", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;"))
    private Entity react$travelling(Entity entity, DimensionTransition transition, Operation<Entity> original) {
        if (!ModPortalHost.allow(entity, transition.newLevel(), transition.pos().x, transition.pos().y, transition.pos().z)) {
            return null;
        }
        return original.call(entity, transition);
    }
}
