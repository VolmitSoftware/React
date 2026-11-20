package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModExplosionHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Where Bukkit fires its block and entity explode events: right before an explosion breaks its blocks. */
@Mixin(Explosion.class)
public abstract class ExplosionHooks {
    @Shadow @Final private Level level;
    @Shadow @Final private double x;
    @Shadow @Final private double y;
    @Shadow @Final private double z;
    @Shadow @Final private float radius;
    @Shadow @Final private Entity source;
    @Shadow @Final private Explosion.BlockInteraction blockInteraction;
    @Shadow @Final private ObjectArrayList<BlockPos> toBlow;

    @Inject(method = "finalizeExplosion", at = @At("HEAD"), require = 0)
    private void react$breaking(boolean spawnParticles, CallbackInfo callback) {
        if (!(level instanceof ServerLevel serverLevel) || blockInteraction == Explosion.BlockInteraction.KEEP) {
            return;
        }
        boolean destroys = blockInteraction == Explosion.BlockInteraction.DESTROY
                || blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY;
        ModExplosionHost.exploding(serverLevel, new Vec3(x, y, z), radius, source == null, destroys,
                blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY, toBlow);
    }
}
