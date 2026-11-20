package art.arcane.react.mod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;

final class VanillaCompat {
    private VanillaCompat() {
    }

    static void dropFromPlayer(ServerPlayer player, ItemStack item) {
        player.drop(item, false);
    }

    static boolean isTrader(EntityType<?> type) {
        return type == EntityTypes.VILLAGER || type == EntityTypes.WANDERING_TRADER;
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
}
