package art.arcane.react.mod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

final class VanillaCompat {
    private VanillaCompat() {
    }

    static void dropFromPlayer(ServerPlayer player, ItemStack item) {
        player.drop(item, false);
    }

    static boolean isTrader(EntityType<?> type) {
        return type == EntityType.VILLAGER || type == EntityType.WANDERING_TRADER;
    }
}
