package art.arcane.react.mod;

import art.arcane.react.platform.SettingsScreen;
import art.arcane.react.platform.SettingsScreenHost;
import art.arcane.react.platform.SettingsTile;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ModSettingsScreenHost implements SettingsScreenHost {
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
        int columns = screen.columns();
        int rows = screen.dropper() ? 3 : Math.max(1, Math.min(6, screen.rows()));
        int size = rows * columns;
        String title = screen.title() == null ? "" : screen.title();
        Map<Integer, SettingsTile> tiles = new HashMap<>();
        for (SettingsTile tile : screen.tiles()) {
            int slot = tile.slot(columns);
            if (slot >= 0 && slot < size) {
                tiles.put(slot, tile);
            }
        }
        ItemStack background = new ItemStack(item(screen.background(), "black_stained_glass_pane"));
        VanillaItems.name(background, Component.literal(" "));
        List<ItemStack> items = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) {
            SettingsTile tile = tiles.get(slot);
            items.add(tile == null ? background.copy() : stack(tile));
        }
        if (screen.dropper()) {
            if (player.containerMenu instanceof ModSettingsDropperMenu current && current.reusableFor(title)) {
                current.show(tiles, handler, items);
                return;
            }
            player.openMenu(new SimpleMenuProvider((int id, Inventory inventory, Player opener) -> {
                ModSettingsDropperMenu menu = new ModSettingsDropperMenu(id, inventory, player, ticks, title);
                menu.show(tiles, handler, items);
                return menu;
            }, ModLegacyText.component(title)));
            return;
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
            VanillaItems.name(stack, ModLegacyText.component(tile.name()));
        }
        if (!tile.lore().isEmpty()) {
            List<Component> lines = new ArrayList<>(tile.lore().size());
            for (String line : tile.lore()) {
                lines.add(ModLegacyText.component(line));
            }
            VanillaItems.lore(stack, lines);
        }
        if (tile.enchanted()) {
            VanillaItems.glint(stack);
        }
        if (tile.dyeColor() != null) {
            VanillaItems.dyedColor(stack, tile.dyeColor());
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
