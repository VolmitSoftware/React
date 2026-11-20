package art.arcane.react.mod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * The six-row chest behind {@code /react config gui} on mods. Item movement is blocked; clicks go to the button in the
 * slot as left, right or shift clicks.
 */
final class ModSettingsMenu extends ReactChestMenu {
    static final int ROWS = 6;

    enum Click {
        LEFT,
        RIGHT,
        SHIFT
    }

    @FunctionalInterface
    interface Button {
        void click(Click click);
    }

    private final ServerPlayer viewer;
    private final String title;
    private Map<Integer, Button> buttons = Map.of();

    ModSettingsMenu(int id, Inventory inventory, ServerPlayer viewer, String title) {
        super(MenuType.GENERIC_9x6, id, inventory, new SimpleContainer(ROWS * 9), ROWS);
        this.viewer = viewer;
        this.title = title;
    }

    boolean reusableFor(String title) {
        return this.title.equals(title);
    }

    void show(List<ItemStack> items, Map<Integer, Button> buttons) {
        this.buttons = Map.copyOf(buttons);
        for (int slot = 0; slot < items.size(); slot++) {
            if (!ItemStack.matches(getContainer().getItem(slot), items.get(slot))) {
                getContainer().setItem(slot, items.get(slot));
            }
        }
        broadcastChanges();
    }

    @Override
    void clicked(int slot, int button, MenuClick input, Player player) {
        if (player != viewer || viewer.containerMenu != this || slot < 0 || slot >= ROWS * 9) {
            return;
        }
        Button target = buttons.get(slot);
        Click click = input == MenuClick.QUICK_MOVE ? Click.SHIFT
                : input == MenuClick.PICKUP ? (button == 0 ? Click.LEFT : button == 1 ? Click.RIGHT : null)
                : null;
        if (target != null && click != null) {
            target.click(click);
        }
        if (viewer.containerMenu != this) {
            viewer.containerMenu.broadcastFullState();
        } else {
            broadcastFullState();
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
