package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPlayerHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Damage from a player or their projectile to the non-living entities Bukkit also fires EntityDamageByEntityEvent
 * for: item frames and other attached entities, end crystals, items, experience orbs, fireballs, shulker bullets and
 * interaction entities. A cancelled hit deals none.
 */
@Mixin({BlockAttachedEntity.class, ItemFrame.class, EndCrystal.class, ItemEntity.class, ExperienceOrb.class,
        AbstractHurtingProjectile.class, ShulkerBullet.class, Interaction.class})
public abstract class EntityAttackHooks {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$attackEntity(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if (ModPlayerHost.cancelsEntityAttack(this, source)) {
            callback.setReturnValue(false);
        }
    }
}
