package art.arcane.react.util.data;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.data.BSupport;
import art.arcane.react.core.nms.container.BlockProperty;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.List;

public class B {
    private static final BSupportImpl BASE = new BSupportImpl();

    private static final class BSupportImpl extends BSupport<BlockProperty> {
    }

    public static BlockData toDeepSlateOre(BlockData block, BlockData ore) {
        return BASE.toDeepSlateOre(block, ore);
    }

    public static boolean isDeepSlate(BlockData blockData) {
        return BASE.isDeepSlate(blockData);
    }

    public static boolean isOre(BlockData blockData) {
        return BASE.isOre(blockData);
    }

    public static boolean canPlaceOnto(Material mat, Material onto) {
        return BASE.canPlaceOnto(mat, onto);
    }

    public static boolean isFoliagePlantable(BlockData d) {
        return BASE.isFoliagePlantable(d);
    }

    public static boolean isFoliagePlantable(Material d) {
        return BASE.isFoliagePlantable(d);
    }

    public static boolean isWater(BlockData b) {
        return BASE.isWater(b);
    }

    public static BlockData getAir() {
        return BASE.getAir();
    }

    public static Material getMaterialOrNull(String bdx) {
        return BASE.getMaterialOrNull(bdx);
    }

    public static Material getMaterial(String bdx) {
        return BASE.getMaterial(bdx);
    }

    public static boolean isSolid(BlockData mat) {
        return BASE.isSolid(mat);
    }

    public static BlockData getOrNull(String bdxf) {
        return BASE.getOrNull(bdxf);
    }

    public static BlockData getOrNull(String bdxf, boolean warn) {
        return BASE.getOrNull(bdxf, warn);
    }

    public static BlockData getNoCompat(String bdxf) {
        return BASE.getNoCompat(bdxf);
    }

    public static BlockData get(String bdxf) {
        return BASE.get(bdxf);
    }

    public static boolean isStorage(BlockData mat) {
        return BASE.isStorage(mat);
    }

    public static boolean isStorageChest(BlockData mat) {
        return BASE.isStorageChest(mat);
    }

    public static boolean isLit(BlockData mat) {
        return BASE.isLit(mat);
    }

    public static boolean isUpdatable(BlockData mat) {
        return BASE.isUpdatable(mat);
    }

    public static boolean isFoliage(Material d) {
        return BASE.isFoliage(d);
    }

    public static boolean isFoliage(BlockData d) {
        return BASE.isFoliage(d);
    }

    public static boolean isDecorant(BlockData m) {
        return BASE.isDecorant(m);
    }

    public static KList<BlockData> get(KList<String> find) {
        return BASE.get(find);
    }

    public static boolean isFluid(BlockData d) {
        return BASE.isFluid(d);
    }

    public static boolean isAirOrFluid(BlockData d) {
        return BASE.isAirOrFluid(d);
    }

    public static boolean isAir(BlockData d) {
        return BASE.isAir(d);
    }

    public synchronized static String[] getBlockTypes() {
        return BASE.getBlockTypes();
    }

    public synchronized static KMap<List<String>, List<BlockProperty>> getBlockStates() {
        return BASE.getBlockStates();
    }

    public static String[] getItemTypes() {
        return BASE.getItemTypes();
    }

    public static boolean isWaterLogged(BlockData b) {
        return BASE.isWaterLogged(b);
    }

    public static void registerCustomBlockData(String namespace, String key, BlockData blockData) {
        BASE.registerCustomBlockData(namespace, key, blockData);
    }

    public static boolean isVineBlock(BlockData data) {
        return BASE.isVineBlock(data);
    }
}
