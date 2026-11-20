package art.arcane.react.mod;

import art.arcane.react.platform.SettingsScreen;
import art.arcane.react.platform.SettingsScreenHost;
import art.arcane.react.platform.SettingsTile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ModSettingsScreenHost implements SettingsScreenHost {
    private static final int WIDTH = 9;
    private final MinecraftServer server;
    private final ModTicks ticks;

    ModSettingsScreenHost(MinecraftServer server, ModTicks ticks) {
        this.server = server;
        this.ticks = ticks;
    }

    @Override
    public void open(UUID viewerId, SettingsScreen screen, ClickHandler handler) {
        ServerPlayer player = player(viewerId);
        if (player == null || screen == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, screen.rows()));
        String title = screen.title() == null ? "" : screen.title();
        Map<Integer, SettingsTile> tiles = new HashMap<>();
        for (SettingsTile tile : screen.tiles()) {
            int slot = tile.slot();
            if (slot >= 0 && slot < rows * WIDTH) {
                tiles.put(slot, tile);
            }
        }
        ItemStack background = new ItemStack(item(screen.background(), "black_stained_glass_pane"));
        background.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        List<ItemStack> items = new ArrayList<>(rows * WIDTH);
        for (int slot = 0; slot < rows * WIDTH; slot++) {
            SettingsTile tile = tiles.get(slot);
            items.add(tile == null ? background.copy() : stack(tile));
        }
        if (player.containerMenu instanceof ModSettingsMenu current && current.reusableFor(rows, title)) {
            current.show(tiles, handler, items);
            return;
        }
        player.openMenu(new SimpleMenuProvider((int id, Inventory inventory, Player opener) -> {
            ModSettingsMenu menu = new ModSettingsMenu(id, inventory, player, ticks, rows, title);
            menu.show(tiles, handler, items);
            return menu;
        }, ModLegacyText.component(title)));
    }

    private static ItemStack stack(SettingsTile tile) {
        ItemStack stack = new ItemStack(item(tile.icon(), tile.fallbackIcon()));
        if (tile.name() != null) {
            stack.set(DataComponents.CUSTOM_NAME, ModLegacyText.component(tile.name()));
        }
        if (!tile.lore().isEmpty()) {
            List<Component> lines = new ArrayList<>(tile.lore().size());
            for (String line : tile.lore()) {
                lines.add(ModLegacyText.component(line));
            }
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        if (tile.enchanted()) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    static Item item(String icon, String fallback) {
        Item item = icon == null ? Items.AIR : VanillaApi.itemOrAir(icon);
        if (item == Items.AIR && fallback != null) {
            item = VanillaApi.itemOrAir(fallback);
        }
        return item == Items.AIR ? Items.CHEST : item;
    }

    @Override
    public void close(UUID viewerId) {
        ServerPlayer player = player(viewerId);
        if (player != null) {
            player.closeContainer();
        }
    }

    @Override
    public void playPageTurn(UUID viewerId) {
        ServerPlayer player = player(viewerId);
        if (player == null) {
            return;
        }
        pageTurn(player, 1.1F, 1.255F);
        pageTurn(player, 0.7F, 1.455F);
        pageTurn(player, 0.3F, 1.855F);
    }

    private static void pageTurn(ServerPlayer player, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BOOK_PAGE_TURN),
                SoundSource.MASTER, player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
    }

    private ServerPlayer player(UUID viewerId) {
        return viewerId == null ? null : server.getPlayerList().getPlayer(viewerId);
    }
}
