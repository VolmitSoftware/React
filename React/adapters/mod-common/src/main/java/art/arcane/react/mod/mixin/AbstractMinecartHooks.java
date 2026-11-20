package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModMinecartDrag;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The derailed and airborne drag a minecart applies off rails, where CraftBukkit multiplies by its derailed and flying
 * velocity modifiers.
 */
@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartHooks implements ModMinecartDrag {
    @Unique
    private boolean react$offRailStopped;

    @Override
    public boolean react$offRailStopped() {
        return react$offRailStopped;
    }

    @Override
    public void react$setOffRailStopped(boolean stopped) {
        react$offRailStopped = stopped;
    }

    @ModifyArg(method = "comeOffTrack", require = 0, index = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"))
    private double react$offRailDrag(double factor) {
        return react$offRailStopped ? 0D : factor;
    }
}
