package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModRedstoneHost;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Levers pulled by players and explosions, where CraftBukkit fires its block redstone event. */
@Mixin(LeverBlock.class)
public abstract class RedstoneLeverHooks {
    @WrapWithCondition(method = {"useWithoutItem", "onExplosionHit"}, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/LeverBlock;pull(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V"))
    private boolean react$pulling(LeverBlock lever, BlockState state, Level level, BlockPos pos, Player player) {
        return !ModRedstoneHost.held(level, pos, state, !state.getValue(LeverBlock.POWERED));
    }
}
