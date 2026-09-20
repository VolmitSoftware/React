package art.arcane.react.test;

import art.arcane.volmlib.nativelib.monitor.BrewingTickResult;
import art.arcane.volmlib.nativelib.monitor.FurnaceTickResult;
import art.arcane.volmlib.nativelib.monitor.NativeMonitor;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.monitor.NativeWorldAccess;
import art.arcane.volmlib.nativelib.monitor.EntityRangeSettings;
import art.arcane.react.nms.NmsBridges;
import art.arcane.volmlib.nativelib.monitor.TickDecision;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Zombie;
import org.bukkit.World;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Chest;
import org.bukkit.block.Furnace;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

import java.util.logging.Level;

public final class RuntimeProbe extends JavaPlugin {
    private NativeMonitor bridge;
    private World world;
    private int furnaceTicks;
    private int brewingTicks;
    private int hopperTicks;

    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskLater(this, this::begin, 20L);
    }

    @Override
    public void onDisable() {
        if (bridge != null) {
            bridge.uninstallFurnaceTickHook();
            bridge.uninstallBrewingTickHook();
            bridge.uninstallHopperTickHook();
        }
        if (world != null) {
            world.setChunkForceLoaded(0, 0, false);
        }
    }

    private void begin() {
        try {
            bridge = NmsBridges.get();
            require(bridge != null, "Native bridge unavailable");
            world = getServer().getWorlds().getFirst();
            world.setChunkForceLoaded(0, 0, true);
            require(bridge.installFurnaceTickHook((target, x, y, z) -> {
                furnaceTicks++;
                return FurnaceTickResult.runAndAdvance(2);
            }), "Furnace hook installation failed");
            require(bridge.installBrewingTickHook((target, x, y, z) -> {
                brewingTicks++;
                return BrewingTickResult.runAndAdvance(2);
            }), "Brewing hook installation failed");
            require(bridge.installHopperTickHook((target, x, y, z) -> {
                hopperTicks++;
                return TickDecision.RUN_VANILLA;
            }), "Hopper hook installation failed");
            placeFurnace();
            placeBrewer();
            placeHopper();
            verifyWorldAccess();
            require(bridge.broadcastMergedExplosion(world, 8, 245, 8, 2F, 16D), "Merged explosion packet failed");
            require(bridge.countPluginChunkTickets(world) >= 0, "Chunk ticket count was invalid");
            getServer().getScheduler().runTaskLater(this, this::verify, 450L);
        } catch (Exception failure) {
            getLogger().log(Level.SEVERE, "RUNTIME PROBE FAILED", failure);
        }
    }

    private void placeFurnace() {
        world.getBlockAt(2, 240, 2).setType(Material.AIR, false);
        world.getBlockAt(2, 240, 2).setType(Material.FURNACE);
        Furnace furnace = (Furnace) world.getBlockAt(2, 240, 2).getState();
        furnace.getInventory().setSmelting(new ItemStack(Material.SAND, 2));
        furnace.getInventory().setFuel(new ItemStack(Material.COAL));
    }

    private void placeBrewer() {
        world.getBlockAt(3, 240, 3).setType(Material.AIR, false);
        world.getBlockAt(3, 240, 3).setType(Material.BREWING_STAND);
        BrewingStand brewer = (BrewingStand) world.getBlockAt(3, 240, 3).getState();
        ItemStack bottle = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) bottle.getItemMeta();
        meta.setBasePotionType(PotionType.WATER);
        bottle.setItemMeta(meta);
        brewer.getInventory().setItem(0, bottle);
        brewer.getInventory().setIngredient(new ItemStack(Material.NETHER_WART));
        brewer.getInventory().setFuel(new ItemStack(Material.BLAZE_POWDER));
    }

    private void placeHopper() {
        world.getBlockAt(4, 241, 4).setType(Material.AIR, false);
        world.getBlockAt(4, 240, 4).setType(Material.AIR, false);
        world.getBlockAt(4, 240, 4).setType(Material.CHEST);
        world.getBlockAt(4, 241, 4).setType(Material.HOPPER);
        Hopper hopper = (Hopper) world.getBlockAt(4, 241, 4).getState();
        hopper.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 4));
    }

    private void verifyWorldAccess() {
        NativeWorldAccess access = NativeAdapters.require(NativeWorldAccess.class);
        EntityRangeSettings ranges = access.entityRanges(world);
        for (EntityRangeSettings.Range type : ranges.supportedRanges()) {
            int baseline = ranges.range(type);
            try {
                ranges.range(type, baseline + 1);
                require(ranges.range(type) == baseline + 1, "Native entity range did not update: " + type);
            } finally {
                ranges.range(type, baseline);
            }
        }
        boolean inactiveVillagers = ranges.tickInactiveVillagers();
        try {
            ranges.tickInactiveVillagers(!inactiveVillagers);
            require(ranges.tickInactiveVillagers() != inactiveVillagers, "Native inactive villager setting did not update");
        } finally {
            ranges.tickInactiveVillagers(inactiveVillagers);
        }
        getLogger().info("NATIVE ENTITY RANGES PASSED");
        world.getBlockAt(6, 240, 6).setType(Material.STONE);
        world.getBlockAt(6, 241, 6).setType(Material.HOPPER);
        NativeWorldAccess.HopperAccess hopper = access.hopper(world, 6, 241, 6);
        require(hopper != null, "Native hopper handle unavailable");
        Item item = world.dropItem(new Location(world, 6.5, 242, 6.5), new ItemStack(Material.IRON_INGOT));
        try {
            require(hopper.addItem(item), "Native hopper insertion failed");
            hopper.cooldown(12);
            require(hopper.cooldown() == 12, "Native hopper cooldown did not update");
            require(!hopper.isEmpty(), "Native hopper insertion did not persist");
        } finally {
            item.remove();
        }
        Zombie mob = world.spawn(new Location(world, 7.5, 241, 7.5), Zombie.class);
        try {
            require(access.setNavigationBudget(mob, 0.4F), "Native navigation budget did not apply");
            access.resetNavigationBudget(mob);
        } finally {
            mob.remove();
        }
        world.getBlockAt(9, 240, 9).setType(Material.STONE);
        world.getBlockAt(9, 241, 9).setType(Material.WATER);
        require(!access.tickFluid(world, 9, 241, 9, false, true), "Disabled water tick was executed");
        require(access.tickFluid(world, 9, 241, 9, true, true), "Native water tick did not execute");
        getLogger().info("NATIVE WORLD ACCESS PASSED");
    }

    private void verify() {
        try {
            require(furnaceTicks > 0, "Furnace hook never ran");
            require(brewingTicks > 0, "Brewing hook never ran");
            require(hopperTicks > 0, "Hopper hook never ran");
            Furnace furnace = (Furnace) world.getBlockAt(2, 240, 2).getState();
            ItemStack result = furnace.getInventory().getResult();
            require(result != null && result.getType() == Material.GLASS && result.getAmount() == 2,
                    "Furnace did not produce both glass items");
            BrewingStand brewer = (BrewingStand) world.getBlockAt(3, 240, 3).getState();
            ItemStack potion = brewer.getInventory().getItem(0);
            require(potion != null && ((PotionMeta) potion.getItemMeta()).getBasePotionType() == PotionType.AWKWARD,
                    "Brewing recipe did not produce an awkward potion");
            Chest chest = (Chest) world.getBlockAt(4, 240, 4).getState();
            require(chest.getInventory().containsAtLeast(new ItemStack(Material.COBBLESTONE), 4),
                    "Hopper failed to transfer its inventory");
            getLogger().info("RUNTIME PROBE PASSED " + bridge.version() + " furnace=" + furnaceTicks
                    + " brewing=" + brewingTicks + " hopper=" + hopperTicks);
        } catch (Exception failure) {
            getLogger().log(Level.SEVERE, "RUNTIME PROBE FAILED", failure);
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
