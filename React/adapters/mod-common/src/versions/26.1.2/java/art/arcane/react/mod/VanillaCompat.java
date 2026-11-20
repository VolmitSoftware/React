package art.arcane.react.mod;

import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

final class VanillaCompat {
    private VanillaCompat() {
    }

    static void dropFromPlayer(ServerPlayer player, ItemStack item) {
        player.drop(item, false);
    }

    static boolean isTrader(EntityType<?> type) {
        return type == EntityType.VILLAGER || type == EntityType.WANDERING_TRADER;
    }

    // Batch A: ai
    /** Spigot's inactive tick: the entity keeps its age and interpolation but runs none of its own logic. */
    static void inactiveTick(Entity entity) {
        entity.setOldPosAndRot();
        entity.tickCount++;
    }

    // Batch A: items
    static void syncVelocity(Entity entity) {
        entity.hurtMarked = true;
    }

    static Packet<?> mergedExplosionPacket(double x, double y, double z, float radius) {
        return new ClientboundExplodePacket(new Vec3(x, y, z), radius, 0, Optional.<Vec3>empty(),
                radius >= 2.0F ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, SoundEvents.GENERIC_EXPLODE,
                WeightedList.<ExplosionParticleInfo>of());
    }
}
