package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModRedstoneHost;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tripwire hooks switching power, where CraftBukkit fires its block redstone event for the receiving hook and then for
 * the emitting hook: a held hook keeps its state, sounds and neighbour updates, as on Bukkit.
 */
@Mixin(TripWireHookBlock.class)
public abstract class RedstoneTripwireHooks {
    private static final String SET_AND_UPDATE = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z";
    private static final String SET = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z";
    private static final String NOTIFY = "Lnet/minecraft/world/level/block/TripWireHookBlock;notifyNeighbors(Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)V";
    private static final String EMIT = "Lnet/minecraft/world/level/block/TripWireHookBlock;emitState(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ZZZZ)V";

    @WrapWithCondition(method = "calculateState", require = 0,
            at = {@At(value = "INVOKE", target = SET_AND_UPDATE, ordinal = 0), @At(value = "INVOKE", target = SET, ordinal = 0)})
    private static boolean react$receiver(Level level, BlockPos receiver, BlockState receiverState, @Local(argsOnly = true, ordinal = 0) BlockState state,
                                          @Share("receiverHeld") LocalBooleanRef receiverHeld) {
        boolean wasPowered = state.getOptionalValue(TripWireHookBlock.POWERED).orElse(false);
        boolean powered = receiverState.getOptionalValue(TripWireHookBlock.POWERED).orElse(false);
        receiverHeld.set(wasPowered != powered && ModRedstoneHost.held(level, receiver, receiverState, powered));
        return !receiverHeld.get();
    }

    @WrapWithCondition(method = "calculateState", require = 0, at = @At(value = "INVOKE", target = NOTIFY, ordinal = 0))
    private static boolean react$receiverNeighbours(Block block, Level level, BlockPos pos, Direction direction,
                                                    @Share("receiverHeld") LocalBooleanRef receiverHeld) {
        return !receiverHeld.get();
    }

    @WrapWithCondition(method = "calculateState", require = 0, at = @At(value = "INVOKE", target = EMIT, ordinal = 0))
    private static boolean react$receiverEmit(Level level, BlockPos pos, boolean attached, boolean powered, boolean wasAttached, boolean wasPowered,
                                              @Share("receiverHeld") LocalBooleanRef receiverHeld) {
        return !receiverHeld.get();
    }

    @WrapWithCondition(method = "calculateState", require = 0, at = @At(value = "INVOKE", target = EMIT, ordinal = 1))
    private static boolean react$emitter(Level level, BlockPos pos, boolean attached, boolean powered, boolean wasAttached, boolean wasPowered,
                                         @Local(argsOnly = true, ordinal = 0) BlockState state, @Share("emitterHeld") LocalBooleanRef emitterHeld) {
        emitterHeld.set(wasPowered != powered && ModRedstoneHost.held(level, pos, state, powered));
        return !emitterHeld.get();
    }

    @WrapWithCondition(method = "calculateState", require = 0,
            at = {@At(value = "INVOKE", target = SET_AND_UPDATE, ordinal = 1), @At(value = "INVOKE", target = SET, ordinal = 1)})
    private static boolean react$emitterState(Level level, BlockPos pos, BlockState newState, @Share("emitterHeld") LocalBooleanRef emitterHeld) {
        return !emitterHeld.get();
    }

    @WrapWithCondition(method = "calculateState", require = 0, at = @At(value = "INVOKE", target = NOTIFY, ordinal = 1))
    private static boolean react$emitterNeighbours(Block block, Level level, BlockPos pos, Direction direction,
                                                   @Share("emitterHeld") LocalBooleanRef emitterHeld) {
        return !emitterHeld.get();
    }
}
