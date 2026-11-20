package art.arcane.react.mod;

import art.arcane.react.platform.SettingsAction;
import art.arcane.react.platform.SettingsClick;
import art.arcane.react.platform.SettingsScreenHost;
import art.arcane.react.platform.SettingsTile;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Turns clicks in a React menu into tile actions the way the Bukkit window does: a left click acts on the next tick
 * and two left clicks inside one tick act on neither; right and shift clicks act at once.
 */
final class ModSettingsClicks {
    private final ServerPlayer viewer;
    private final ModTicks ticks;
    private Map<Integer, SettingsTile> tiles;
    private SettingsScreenHost.ClickHandler handler;
    private int clickCheck;

    ModSettingsClicks(ServerPlayer viewer, ModTicks ticks) {
        this.viewer = viewer;
        this.ticks = ticks;
    }

    boolean ready() {
        return tiles != null;
    }

    void show(Map<Integer, SettingsTile> tiles, SettingsScreenHost.ClickHandler handler) {
        this.tiles = tiles;
        this.handler = handler;
    }

    void click(int slot, int button, ReactChestMenu.MenuClick input) {
        SettingsTile tile = tiles.get(slot);
        if (input == ReactChestMenu.MenuClick.PICKUP && button == 0) {
            leftClick(tile);
        } else if (input == ReactChestMenu.MenuClick.PICKUP && button == 1) {
            call(tile, SettingsClick.RIGHT);
        } else if (input == ReactChestMenu.MenuClick.QUICK_MOVE && button == 0) {
            call(tile, SettingsClick.SHIFT_LEFT);
        } else if (input == ReactChestMenu.MenuClick.QUICK_MOVE && button == 1) {
            call(tile, SettingsClick.SHIFT_RIGHT);
        }
    }

    void closed() {
        SettingsScreenHost.ClickHandler current = handler;
        if (current != null) {
            current.onClosed(viewer.getUUID());
        }
    }

    private void leftClick(SettingsTile tile) {
        clickCheck++;
        if (clickCheck == 1) {
            ticks.later(() -> {
                if (clickCheck == 1) {
                    clickCheck = 0;
                    call(tile, SettingsClick.LEFT);
                }
            });
        } else if (clickCheck == 2) {
            ticks.later(() -> clickCheck = 0);
        }
    }

    private void call(SettingsTile tile, SettingsClick click) {
        SettingsAction action = tile == null ? null : tile.actionFor(click);
        SettingsScreenHost.ClickHandler current = handler;
        if (action != null && current != null) {
            current.onClick(viewer.getUUID(), tile, click, action);
        }
    }
}
