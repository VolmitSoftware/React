package art.arcane.react.mod;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DispenserMenu;

/** A 3x3 menu that sees each click as a {@link ReactChestMenu.MenuClick}, like {@link ReactChestMenu}. */
abstract class ReactDispenserMenu extends DispenserMenu {
    ReactDispenserMenu(int id, Inventory inventory, Container container) {
        super(id, inventory, container);
    }

    @Override
    public final void clicked(int slot, int button, ClickType input, Player player) {
        clicked(slot, button, ReactChestMenu.kind(input), player);
    }

    abstract void clicked(int slot, int button, ReactChestMenu.MenuClick click, Player player);
}
