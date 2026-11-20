package art.arcane.react.mod;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Static bridge between the map mixins and the running map host. Every call is a no-op while React is stopped. */
public final class ModMapHooks {
    private static final Set<Integer> CLAIMED = ConcurrentHashMap.newKeySet();
    private static final int CURSOR_INTERVAL_TICKS = 5;
    private static final Map<Carried, int[]> CARRIED = new ConcurrentHashMap<>();

    private record Carried(UUID player, int mapId) {
    }
    private static volatile Listener listener;

    interface Listener {
        void frameChanged(ItemFrame frame);

        void trackingStarted(ServerLevel level, Entity entity);

        void trackingEnded(ServerLevel level, Entity entity);

        /** Renders the map for the player: one update with its cursors and the pixels the player has not seen yet. */
        Packet<?> carried(int mapId, byte scale, boolean locked, ServerPlayer player);
    }

    private ModMapHooks() {
    }

    static void bind(Listener bound) {
        listener = bound;
    }

    static void unbind(Listener bound) {
        if (listener == bound) {
            listener = null;
            CLAIMED.clear();
            CARRIED.clear();
        }
    }

    static void claim(int mapId) {
        CLAIMED.add(mapId);
    }

    static void release(int mapId) {
        CLAIMED.remove(mapId);
        CARRIED.keySet().removeIf((Carried carried) -> carried.mapId() == mapId);
    }

    public static boolean isClaimed(int mapId) {
        return CLAIMED.contains(mapId);
    }

    /**
     * The vanilla update for a React map a player carries, polled every tick for every map in the inventory. As on
     * Bukkit, every fifth poll renders the map for the player and sends one packet with its cursors and any pixels the
     * player has not seen yet. Maps shown only in an item frame keep React's own frame delivery. Server thread.
     */
    public static Packet<?> carriedUpdate(MapItemSavedData data, int mapId, Player player) {
        Listener current = listener;
        if (current == null || !(player instanceof ServerPlayer) || !carries(player, mapId)) {
            return null;
        }
        int[] state = CARRIED.computeIfAbsent(new Carried(player.getUUID(), mapId), (Carried ignored) -> new int[1]);
        if (state[0]++ % CURSOR_INTERVAL_TICKS != 0) {
            return null;
        }
        return current.carried(mapId, data.scale, data.locked, (ServerPlayer) player);
    }

    private static boolean carries(Player player, int mapId) {
        if (VanillaItems.mapId(player.getMainHandItem()) == mapId || VanillaItems.mapId(player.getOffhandItem()) == mapId) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (VanillaItems.mapId(inventory.getItem(slot)) == mapId) {
                return true;
            }
        }
        return false;
    }

    static void forget(UUID player) {
        CARRIED.keySet().removeIf((Carried carried) -> carried.player().equals(player));
    }

    public static void frameChanged(ItemFrame frame) {
        Listener current = listener;
        if (current != null && frame.level() instanceof ServerLevel) {
            current.frameChanged(frame);
        }
    }

    public static void trackingStarted(Entity entity) {
        Listener current = listener;
        if (current != null && entity.level() instanceof ServerLevel level) {
            current.trackingStarted(level, entity);
        }
    }

    public static void trackingEnded(Entity entity) {
        Listener current = listener;
        if (current != null && entity.level() instanceof ServerLevel level) {
            current.trackingEnded(level, entity);
        }
    }
}
