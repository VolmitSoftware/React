package art.arcane.react.mod;

import art.arcane.react.platform.SettingsScreenHost;
import art.arcane.react.platform.SettingsTile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * The chest behind {@code /react config gui} and {@code /react config monitor} on mods. Item movement is blocked and
 * clicks reach the menu through {@link ModSettingsClicks}.
 */
final class ModSettingsMenu extends ReactChestMenu {
    private static final List<MenuType<ChestMenu>> TYPES = List.of(MenuType.GENERIC_9x1, MenuType.GENERIC_9x2,
            MenuType.GENERIC_9x3, MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6);

    private final ServerPlayer viewer;
    private final ModSettingsClicks clicks;
    private final int rows;
    private final String title;

    ModSettingsMenu(int id, Inventory inventory, ServerPlayer viewer, ModTicks ticks, int rows, String title) {
        super(TYPES.get(rows - 1), id, inventory, new SimpleContainer(rows * 9), rows);
        this.viewer = viewer;
        this.clicks = new ModSettingsClicks(viewer, ticks);
        this.rows = rows;
        this.title = title;
    }

    boolean reusableFor(int rows, String title) {
        return this.rows == rows && this.title.equals(title);
    }

    void show(Map<Integer, SettingsTile> tiles, SettingsScreenHost.ClickHandler handler, List<ItemStack> items) {
        clicks.show(tiles, handler);
        for (int slot = 0; slot < items.size(); slot++) {
            if (!ItemStack.matches(getContainer().getItem(slot), items.get(slot))) {
                getContainer().setItem(slot, items.get(slot));
            }
        }
        broadcastChanges();
    }

    @Override
    void clicked(int slot, int button, MenuClick input, Player player) {
        if (player != viewer || viewer.containerMenu != this || slot < 0 || slot >= rows * 9 || !clicks.ready()) {
            return;
        }
        clicks.click(slot, button, input);
        if (viewer.containerMenu != this) {
            viewer.containerMenu.broadcastFullState();
        } else {
            broadcastFullState();
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player == viewer) {
            clicks.closed();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player == viewer && !viewer.hasDisconnected();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }
}
