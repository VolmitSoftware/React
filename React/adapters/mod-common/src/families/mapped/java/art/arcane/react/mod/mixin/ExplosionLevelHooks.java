package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModExplosionHost;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Primed TNT joining the world, and the explosion packets a level sends once an explosion is done. */
@Mixin(ServerLevel.class)
public abstract class ExplosionLevelHooks {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$priming(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof PrimedTnt tnt && !ModExplosionHost.priming(tnt)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/Holder;)Lnet/minecraft/world/level/Explosion;",
            cancellable = true, require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Explosion;interactsWithBlocks()Z"))
    private void react$packets(Entity source, DamageSource damageSource, ExplosionDamageCalculator damageCalculator,
                               double x, double y, double z, float r, boolean fire, Level.ExplosionInteraction interactionType,
                               ParticleOptions smallExplosionParticles, ParticleOptions largeExplosionParticles,
                               Holder<SoundEvent> explosionSound, CallbackInfoReturnable<Explosion> callback,
                               @Local Explosion explosion) {
        if (ModExplosionHost.suppressPackets((ServerLevel) (Object) this, x, y, z, r)) {
            callback.setReturnValue(explosion);
        }
    }
}
