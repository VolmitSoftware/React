package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModMobStackHost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bukkit's EntityChangeBlockEvent for an enderman picking up a block; the class was renamed in 26.3. */
@Pseudo
@Mixin(targets = {"net.minecraft.world.entity.monster.Enderman$EndermanTakeBlockGoal",
        "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal"})
public abstract class EndermanTakeBlockGoalHooks {
    @Inject(method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"),
            cancellable = true, require = 0)
    private void react$taking(CallbackInfo callback) {
        if (!ModMobStackHost.allowStateChange(ModMobStackHost.goalOwner(this))) {
            callback.cancel();
        }
    }
}
