package art.arcane.react.mod;

import art.arcane.react.platform.MapHubIcon;
import art.arcane.react.platform.MapHubScreen;
import art.arcane.react.platform.MapHubScreenHost;
import art.arcane.react.platform.MapHubTile;
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

/** The React map hub as a vanilla chest menu, laid out and labelled exactly like the Bukkit window. */
final class ModMapHubScreenHost implements MapHubScreenHost {
    private static final int WIDTH = 9;
    private final MinecraftServer server;
    private final ModTicks ticks;

    ModMapHubScreenHost(MinecraftServer server, ModTicks ticks) {
        this.server = server;
        this.ticks = ticks;
    }

    @Override
    public void open(UUID viewerId, MapHubScreen screen, ClickHandler handler) {
        ServerPlayer player = player(viewerId);
        if (player == null || screen == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, screen.rows()));
        String title = screen.title() == null ? "" : screen.title();
        Map<Integer, MapHubTile> tiles = new HashMap<>();
        for (MapHubTile tile : screen.tiles()) {
            int slot = tile.slot();
            if (slot >= 0 && slot < rows * WIDTH) {
                tiles.put(slot, tile);
            }
        }
        ItemStack background = new ItemStack(item(screen.background()));
        List<ItemStack> items = new ArrayList<>(rows * WIDTH);
        for (int slot = 0; slot < rows * WIDTH; slot++) {
            MapHubTile tile = tiles.get(slot);
            items.add(tile == null ? background.copy() : stack(tile));
        }
        if (player.containerMenu instanceof ModMapHubMenu current && current.reusableFor(rows, title)) {
            current.show(tiles, handler, items);
            return;
        }
        player.openMenu(new SimpleMenuProvider((int id, Inventory inventory, Player opener) -> {
            ModMapHubMenu menu = new ModMapHubMenu(id, inventory, player, ticks, rows, title);
            menu.show(tiles, handler, items);
            return menu;
        }, ModLegacyText.component(title)));
    }

    private static ItemStack stack(MapHubTile tile) {
        ItemStack stack = new ItemStack(item(tile.icon()));
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
        return stack;
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

    private static Item vanilla(String path) {
        return VanillaApi.vanillaItem(path);
    }

    static Item item(MapHubIcon icon) {
        return switch (icon) {
            case BACKGROUND -> vanilla("black_stained_glass_pane");
            case ALL_MAPS, GROUP_MISC -> Items.FILLED_MAP;
            case MEGAMAP_SUMMARY -> Items.ITEM_FRAME;
            case BACK, PREVIOUS, NEXT -> Items.ARROW;
            case PAGE_INFO -> Items.PAPER;
            case GROUP_ADAPT -> Items.BOOKSHELF;
            case GROUP_IRIS -> Items.OAK_SAPLING;
            case GROUP_REACT -> Items.REDSTONE_TORCH;
            case GROUP_WORMHOLES -> Items.ENDER_PEARL;
            case GROUP_GLOSS -> Items.PAINTING;
            case GROUP_HIDDENORE -> Items.DIAMOND_ORE;
            case GROUP_BILETOOLS -> vanilla("lime_dye");
            case GROUP_ANIMUS -> Items.ARMOR_STAND;
            case GROUP_TICK -> Items.CLOCK;
            case GROUP_DIMENSIONS -> Items.RECOVERY_COMPASS;
            case GROUP_MEMORY -> Items.EXPERIENCE_BOTTLE;
            case GROUP_PLAYERS -> Items.PLAYER_HEAD;
            case GROUP_ENTITIES -> Items.ZOMBIE_HEAD;
            case GROUP_CHUNKS -> Items.MAP;
            case GROUP_REDSTONE -> Items.REDSTONE;
            case GROUP_HOPPERS -> Items.HOPPER;
            case GROUP_FLUIDS -> Items.WATER_BUCKET;
            case GROUP_PHYSICS -> Items.ANVIL;
            case GROUP_EVENTS -> Items.NOTE_BLOCK;
            case GROUP_SCHEDULER -> Items.COMPARATOR;
            case GROUP_CPU -> Items.BLAST_FURNACE;
            case GROUP_SAMPLER_TRENDS -> Items.COMPASS;
            case GROUP_WORLD_OVERLAYS -> Items.CARTOGRAPHY_TABLE;
            case GROUP_FALLBACK -> Items.BARRIER;
        };
    }
}
