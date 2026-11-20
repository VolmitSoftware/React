package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModMobStackHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bukkit's CreeperPowerEvent for lightning and its EXPLODE removal, for mob stacking. */
@Mixin(Creeper.class)
public abstract class CreeperHooks {
    @Inject(method = "thunderHit", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$charging(ServerLevel level, LightningBolt lightning, CallbackInfo callback) {
        Creeper creeper = (Creeper) (Object) this;
        if (!creeper.isPowered() && !ModMobStackHost.allowStateChange(creeper)) {
            callback.cancel();
        }
    }

    @Inject(method = "explodeCreeper",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Creeper;discard()V"),
            require = 0)
    private void react$exploded(CallbackInfo callback) {
        ModMobStackHost.exploded((Creeper) (Object) this);
    }
}
