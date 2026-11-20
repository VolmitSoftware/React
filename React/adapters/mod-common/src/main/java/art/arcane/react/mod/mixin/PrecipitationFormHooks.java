package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModGrowthHost;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Snow and ice forming from the weather, where Bukkit fires its block form event. */
@Mixin(ServerLevel.class)
public abstract class PrecipitationFormHooks {
    @WrapOperation(method = "tickPrecipitation", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean react$form(ServerLevel level, BlockPos pos, BlockState formed, Operation<Boolean> original) {
        return ModGrowthHost.allowForm(level, pos, formed) && original.call(level, pos, formed);
    }
}
