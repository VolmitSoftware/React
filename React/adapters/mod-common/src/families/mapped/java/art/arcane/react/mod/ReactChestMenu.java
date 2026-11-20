package art.arcane.react.mod;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;

/** A chest menu that sees each click as a {@link MenuClick}, since the vanilla click type was renamed after 1.21.1. */
abstract class ReactChestMenu extends ChestMenu {
    ReactChestMenu(MenuType<ChestMenu> type, int id, Inventory inventory, Container container, int rows) {
        super(type, id, inventory, container, rows);
    }

    @Override
    public final void clicked(int slot, int button, ClickType input, Player player) {
        clicked(slot, button, kind(input), player);
    }

    abstract void clicked(int slot, int button, MenuClick click, Player player);

    static MenuClick kind(ClickType input) {
        return switch (input) {
            case PICKUP -> MenuClick.PICKUP;
            case PICKUP_ALL -> MenuClick.PICKUP_ALL;
            case QUICK_MOVE -> MenuClick.QUICK_MOVE;
            case CLONE -> MenuClick.CLONE;
            default -> MenuClick.OTHER;
        };
    }

    enum MenuClick {
        PICKUP,
        PICKUP_ALL,
        QUICK_MOVE,
        CLONE,
        OTHER
    }
}
