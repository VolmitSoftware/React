package art.arcane.react.mod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Item names, lore, custom data and map storage before data components: everything lives in the item's NBT tag. */
final class VanillaItems {
    private static final String DISPLAY = "display";
    private static final String LORE = "Lore";
    private static final String MAP = "map";
    private static final String ENCHANTMENTS = "Enchantments";
    private static final String HIDE_FLAGS = "HideFlags";

    private VanillaItems() {
    }

    static void dyedColor(ItemStack item, int rgb) {
        item.getOrCreateTagElement(DISPLAY).putInt("color", rgb);
    }

    static void name(ItemStack item, Component name) {
        item.setHoverName(name);
    }

    static void clearName(ItemStack item) {
        item.resetHoverName();
    }

    static boolean hasName(ItemStack item) {
        return item.hasCustomHoverName();
    }

    static void lore(ItemStack item, List<Component> lines) {
        ListTag lore = new ListTag();
        for (Component line : lines) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        item.getOrCreateTagElement(DISPLAY).put(LORE, lore);
    }

    static List<Component> lore(ItemStack item) {
        CompoundTag display = item.getTagElement(DISPLAY);
        if (display == null || !display.contains(LORE, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag lore = display.getList(LORE, Tag.TAG_STRING);
        List<Component> lines = new ArrayList<>(lore.size());
        for (int index = 0; index < lore.size(); index++) {
            Component line = Component.Serializer.fromJson(lore.getString(index));
            if (line != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    // An enchantment list with one empty entry shines without naming an enchantment, and hides from the tooltip.
    static void glint(ItemStack item) {
        ListTag enchantments = new ListTag();
        enchantments.add(new CompoundTag());
        item.getOrCreateTag().put(ENCHANTMENTS, enchantments);
        item.getOrCreateTag().putInt(HIDE_FLAGS, item.getOrCreateTag().getInt(HIDE_FLAGS) | ItemStack.TooltipPart.ENCHANTMENTS.getMask());
    }

    // CraftBukkit keeps PublicBukkitValues at the root of the item tag, so the custom data is the tag itself.
    static CompoundTag customData(ItemStack item) {
        CompoundTag tag = item.getTag();
        return tag == null ? new CompoundTag() : tag.copy();
    }

    static void updateCustomData(ItemStack item, Consumer<CompoundTag> update) {
        update.accept(item.getOrCreateTag());
    }

    static int mapId(ItemStack item) {
        Integer id = MapItem.getMapId(item);
        return id == null ? -1 : id;
    }

    static void mapId(ItemStack item, int mapId) {
        item.getOrCreateTag().putInt(MAP, mapId);
    }

    static int allocateMap(ServerLevel storage, MapItemSavedData data) {
        int id = storage.getFreeMapId();
        storage.setMapData(MapItem.makeKey(id), data);
        return id;
    }

    static MapItemSavedData map(ServerLevel storage, int mapId) {
        return storage.getMapData(MapItem.makeKey(mapId));
    }

    static void map(ServerLevel storage, int mapId, MapItemSavedData data) {
        storage.setMapData(MapItem.makeKey(mapId), data);
    }

    static Packet<?> mapPatch(int mapId, int x, int y, int width, int height, byte[] colors) {
        return new ClientboundMapItemDataPacket(mapId, (byte) 0, true, null, new MapItemSavedData.MapPatch(x, y, width, height, colors));
    }

    /** A map update with an empty cursor list and, when {@code colors} is not null, a pixel patch. */
    static Packet<?> carriedMapUpdate(int mapId, byte scale, boolean locked, int x, int y, int width, int height, byte[] colors) {
        return new ClientboundMapItemDataPacket(mapId, scale, locked, List.of(),
                colors == null ? null : new MapItemSavedData.MapPatch(x, y, width, height, colors));
    }
}
