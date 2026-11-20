package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModSpawnReason;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
public abstract class EntityTypeSpawnReasonHooks<T extends Entity> {
    @Inject(method = "create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Lnet/minecraft/world/entity/Entity;",
            at = @At("RETURN"), require = 0)
    private void react$created(Level level, EntitySpawnReason reason, CallbackInfoReturnable<T> callback) {
        ModSpawnReason.record(callback.getReturnValue(), reason);
    }
}
