package art.arcane.react.mod;

import art.arcane.react.api.monitor.configuration.MonitorConfiguration;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Opens the monitor editor of {@code /react config monitor} for a player, with the same screens and chat prompts as
 * on Bukkit.
 */
public final class ModMonitorConfigScreens {
    private ModMonitorConfigScreens() {
    }

    /** {@code saver} persists the player's monitor configuration after every change. */
    public static void open(ServerPlayer player, MonitorConfiguration configuration, Consumer<MonitorConfiguration> saver) {
        if (player == null || configuration == null || saver == null) {
            return;
        }
        ModSettings.current().ifPresent((settings) -> settings.monitorMenu().open(player.getUUID(), configuration, saver));
    }
}
