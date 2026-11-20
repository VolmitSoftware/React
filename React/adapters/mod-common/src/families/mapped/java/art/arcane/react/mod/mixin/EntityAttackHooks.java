package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPlayerHost;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Damage from a player or their projectile to the non-living entities Bukkit also fires EntityDamageByEntityEvent
 * for: item frames and other attached entities, end crystals, items, experience orbs, fireballs and shulker bullets.
 * A cancelled hit deals none.
 */
@Mixin({BlockAttachedEntity.class, ItemFrame.class, EndCrystal.class, ItemEntity.class, ExperienceOrb.class,
        AbstractHurtingProjectile.class, ShulkerBullet.class})
public abstract class EntityAttackHooks {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$attackEntity(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if (ModPlayerHost.cancelsEntityAttack(this, source)) {
            callback.setReturnValue(false);
        }
    }
}
