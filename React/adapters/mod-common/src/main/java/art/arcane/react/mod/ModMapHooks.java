package art.arcane.react.mod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Static bridge between the map mixins and the running map host. Every call is a no-op while React is stopped. */
public final class ModMapHooks {
    private static final Set<Integer> CLAIMED = ConcurrentHashMap.newKeySet();
    private static volatile Listener listener;

    interface Listener {
        void frameChanged(ItemFrame frame);

        void trackingStarted(ServerLevel level, Entity entity);

        void trackingEnded(ServerLevel level, Entity entity);
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
        }
    }

    static void claim(int mapId) {
        CLAIMED.add(mapId);
    }

    static void release(int mapId) {
        CLAIMED.remove(mapId);
    }

    public static boolean isClaimed(int mapId) {
        return CLAIMED.contains(mapId);
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
