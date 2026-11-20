package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModExplosionHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Where Bukkit fires its block and entity explode events: right before an explosion breaks its blocks. */
@Mixin(ServerExplosion.class)
public abstract class ExplosionHooks {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private Vec3 center;
    @Shadow @Final private float radius;
    @Shadow @Final private Entity source;
    @Shadow @Final private Explosion.BlockInteraction blockInteraction;

    @Inject(method = "interactWithBlocks", at = @At("HEAD"), require = 0)
    private void react$breaking(List<BlockPos> targetBlocks, CallbackInfo callback) {
        boolean destroys = blockInteraction == Explosion.BlockInteraction.DESTROY
                || blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY;
        ModExplosionHost.exploding(level, center, radius, source == null, destroys,
                blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY, targetBlocks);
    }
}
