package art.arcane.react.mod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Item names, lore, custom data and map storage in data component form; 1.20.1 carries the NBT form. */
final class VanillaItems {
    private VanillaItems() {
    }

    static void name(ItemStack item, Component name) {
        item.set(DataComponents.CUSTOM_NAME, name);
    }

    static void clearName(ItemStack item) {
        item.remove(DataComponents.CUSTOM_NAME);
    }

    static boolean hasName(ItemStack item) {
        return item.has(DataComponents.CUSTOM_NAME);
    }

    static void lore(ItemStack item, List<Component> lines) {
        item.set(DataComponents.LORE, new ItemLore(lines));
    }

    static List<Component> lore(ItemStack item) {
        ItemLore lore = item.get(DataComponents.LORE);
        return lore == null ? List.of() : lore.lines();
    }

    static void glint(ItemStack item) {
        item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    static CompoundTag customData(ItemStack item) {
        return item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    static void updateCustomData(ItemStack item, Consumer<CompoundTag> update) {
        CustomData.update(DataComponents.CUSTOM_DATA, item, update);
    }

    static int mapId(ItemStack item) {
        MapId id = item.get(DataComponents.MAP_ID);
        return id == null ? -1 : id.id();
    }

    static void mapId(ItemStack item, int mapId) {
        item.set(DataComponents.MAP_ID, new MapId(mapId));
    }

    static int allocateMap(ServerLevel storage, MapItemSavedData data) {
        MapId id = storage.getFreeMapId();
        storage.setMapData(id, data);
        return id.id();
    }

    static MapItemSavedData map(ServerLevel storage, int mapId) {
        return storage.getMapData(new MapId(mapId));
    }

    static void map(ServerLevel storage, int mapId, MapItemSavedData data) {
        storage.setMapData(new MapId(mapId), data);
    }

    static Packet<?> mapPatch(int mapId, int x, int y, int width, int height, byte[] colors) {
        return new ClientboundMapItemDataPacket(new MapId(mapId), (byte) 0, true, Optional.empty(),
                Optional.of(new MapItemSavedData.MapPatch(x, y, width, height, colors)));
    }
}
