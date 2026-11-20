package art.arcane.react.mod;

import art.arcane.react.platform.SettingsScreenHost;
import art.arcane.react.platform.SettingsTile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * The 3x3 dropper screen of a React menu, such as the custom colour picker. Behaves like {@link ModSettingsMenu}.
 */
final class ModSettingsDropperMenu extends ReactDispenserMenu {
    static final int SLOTS = 9;

    private final ServerPlayer viewer;
    private final ModSettingsClicks clicks;
    private final SimpleContainer container;
    private final String title;

    ModSettingsDropperMenu(int id, Inventory inventory, ServerPlayer viewer, ModTicks ticks, String title) {
        this(id, inventory, viewer, ticks, title, new SimpleContainer(SLOTS));
    }

    private ModSettingsDropperMenu(int id, Inventory inventory, ServerPlayer viewer, ModTicks ticks, String title, SimpleContainer container) {
        super(id, inventory, container);
        this.viewer = viewer;
        this.clicks = new ModSettingsClicks(viewer, ticks);
        this.container = container;
        this.title = title;
    }

    boolean reusableFor(String title) {
        return this.title.equals(title);
    }

    void show(Map<Integer, SettingsTile> tiles, SettingsScreenHost.ClickHandler handler, List<ItemStack> items) {
        clicks.show(tiles, handler);
        for (int slot = 0; slot < items.size(); slot++) {
            if (!ItemStack.matches(container.getItem(slot), items.get(slot))) {
                container.setItem(slot, items.get(slot));
            }
        }
        broadcastChanges();
    }

    @Override
    void clicked(int slot, int button, ReactChestMenu.MenuClick input, Player player) {
        if (player != viewer || viewer.containerMenu != this || slot < 0 || slot >= SLOTS || !clicks.ready()) {
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
