package art.arcane.react.mod;

import art.arcane.react.platform.MapHubAction;
import art.arcane.react.platform.MapHubClick;
import art.arcane.react.platform.MapHubScreenHost;
import art.arcane.react.platform.MapHubTile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/** The map hub chest. Item movement is blocked; a click on a tile hands its action to the handler on the next tick. */
final class ModMapHubMenu extends ChestMenu {
    private static final List<MenuType<ChestMenu>> TYPES = List.of(MenuType.GENERIC_9x1, MenuType.GENERIC_9x2,
            MenuType.GENERIC_9x3, MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6);

    private final ServerPlayer viewer;
    private final ModTicks ticks;
    private final int rows;
    private final String title;
    private Map<Integer, MapHubTile> tiles;
    private MapHubScreenHost.ClickHandler handler;

    ModMapHubMenu(int id, Inventory inventory, ServerPlayer viewer, ModTicks ticks, int rows, String title) {
        super(TYPES.get(rows - 1), id, inventory, new SimpleContainer(rows * 9), rows);
        this.viewer = viewer;
        this.ticks = ticks;
        this.rows = rows;
        this.title = title;
    }

    boolean reusableFor(int rows, String title) {
        return this.rows == rows && this.title.equals(title);
    }

    void show(Map<Integer, MapHubTile> tiles, MapHubScreenHost.ClickHandler handler, List<ItemStack> items) {
        this.tiles = tiles;
        this.handler = handler;
        for (int slot = 0; slot < items.size(); slot++) {
            if (!ItemStack.matches(getContainer().getItem(slot), items.get(slot))) {
                getContainer().setItem(slot, items.get(slot));
            }
        }
        broadcastChanges();
    }

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (player != viewer || viewer.containerMenu != this || slot < 0 || slot >= rows * 9 || tiles == null) {
            return;
        }
        MapHubTile tile = tiles.get(slot);
        MapHubClick click = click(input, button);
        MapHubAction action = tile == null || click == null ? null : tile.actionFor(click);
        MapHubScreenHost.ClickHandler current = handler;
        if (action != null && current != null) {
            ticks.later(() -> current.onClick(viewer.getUUID(), tile, click, action));
        }
    }

    static MapHubClick click(ContainerInput input, int button) {
        if (input == ContainerInput.PICKUP) {
            return button == 0 ? MapHubClick.LEFT : button == 1 ? MapHubClick.RIGHT : null;
        }
        if (input == ContainerInput.QUICK_MOVE) {
            return button == 0 ? MapHubClick.SHIFT_LEFT : button == 1 ? MapHubClick.SHIFT_RIGHT : null;
        }
        return input == ContainerInput.CLONE && button == 2 ? MapHubClick.MIDDLE : null;
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
