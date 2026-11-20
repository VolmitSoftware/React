package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModMobStackHost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bukkit's EntityChangeBlockEvent for an enderman placing its block; the class was renamed in 26.3. */
@Pseudo
@Mixin(targets = {"net.minecraft.world.entity.monster.Enderman$EndermanLeaveBlockGoal",
        "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal"})
public abstract class EndermanLeaveBlockGoalHooks {
    @Inject(method = "tick()V",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"),
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z")
            },
            cancellable = true, require = 0)
    private void react$placing(CallbackInfo callback) {
        if (!ModMobStackHost.allowStateChange(ModMobStackHost.goalOwner(this))) {
            callback.cancel();
        }
    }
}
