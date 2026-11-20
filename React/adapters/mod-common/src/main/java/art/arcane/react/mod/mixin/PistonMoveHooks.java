package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModBlockHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A piston about to move its blocks, where Bukkit fires its piston extend and retract events. */
@Mixin(PistonBaseBlock.class)
public abstract class PistonMoveHooks {
    @Redirect(method = "moveBlocks",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;resolve()Z"),
            require = 0)
    private boolean react$resolve(PistonStructureResolver resolver, Level level, BlockPos piston, Direction direction, boolean extending) {
        return resolver.resolve() && ModBlockHost.allowPiston(level, piston, extending, resolver.getToPush());
    }
}
