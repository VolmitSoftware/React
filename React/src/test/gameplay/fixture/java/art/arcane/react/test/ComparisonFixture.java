package art.arcane.react.test;

import art.arcane.react.React;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactProtection;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class ComparisonFixture {
    private final JavaPlugin plugin;
    private final boolean originalMode;
    private final List<Player> actors = new ArrayList<>();
    private World world;
    private UUID protectedCow;

    public ComparisonFixture(JavaPlugin plugin) {
        this.plugin = plugin;
        originalMode = React.instance.isMonitoringOnly();
    }

    public boolean execute(Player admin, String[] args) {
        try {
            switch (args[1]) {
                case "begin" -> begin(admin, args);
                case "snapshot" -> { }
                case "cleanup" -> cleanup();
                default -> throw new IllegalArgumentException("Unknown comparison command");
            }
            admin.sendMessage("REACT_COMPARISON " + args[1] + " " + new Gson().toJson(snapshot()));
        } catch (Exception failure) {
            admin.sendMessage("REACT_COMPARISON ERROR " + failure.getMessage());
        }
        return true;
    }

    private void begin(Player admin, String[] args) {
        if (world != null || args.length != 4) throw new IllegalStateException("Clean up the previous paired fixture");
        for (int index = 2; index < args.length; index++) {
            Player actor = Bukkit.getPlayerExact(args[index]);
            if (actor == null || actor.isOp() || !actor.getName().startsWith("RQA")) throw new IllegalArgumentException("Ordinary RQA actors required");
            actors.add(actor);
        }
        world = new WorldCreator("react_comparison_" + UUID.randomUUID().toString().substring(0, 8)).seed(424242L).generator(new ChunkGenerator() {
            @Override
            public ChunkData generateChunkData(World target, Random random, int x, int z, BiomeGrid biome) { return createChunkData(target); }
        }).createWorld();
        if (world == null) throw new IllegalStateException("Comparison world unavailable");
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) world.getBlockAt(x, 99, z).setType(Material.STONE, false);
        for (int index = 0; index < actors.size(); index++) {
            Player actor = actors.get(index);
            actor.setGameMode(GameMode.SURVIVAL);
            actor.getInventory().clear();
            actor.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 8), new ItemStack(Material.DIRT, 8));
            world.getBlockAt(index, 100, -4).setType(Material.STONE, false);
            world.getBlockAt(index, 101, -4).setType(Material.STONE, false);
            actor.teleport(new Location(world, index + .5, 102, -3.5, 0, 0));
        }
        admin.setGameMode(GameMode.SPECTATOR);
        admin.teleport(new Location(world, .5, 106, .5));
        for (int index = 0; index < 6; index++) {
            final boolean protect = index == 0;
            Cow cow = world.spawn(new Location(world, 2.5 + index * .1, 100, 3.5), Cow.class, entity -> {
                entity.setAI(false);
                entity.setPersistent(true);
                if (protect && !ReactProtection.protect(entity, plugin, ReactOperation.STACK, ReactOperation.TRIM, ReactOperation.PURGE, ReactOperation.SLEEP, ReactOperation.DESPAWN)) throw new IllegalStateException("Protection unavailable");
            });
            if (protect) protectedCow = cow.getUniqueId();
        }
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("monitoringOnly", React.instance.isMonitoringOnly());
        if (world == null) return result;
        result.put("seed", world.getSeed());
        result.put("world", world.getUID().toString());
        result.put("actors", actors.stream().map(Player::getName).toList());
        Map<String, Integer> inventory = new LinkedHashMap<>();
        for (Player actor : actors) for (ItemStack item : actor.getInventory().getContents()) add(inventory, item);
        Map<String, Integer> ground = new LinkedHashMap<>();
        int bundles = 0;
        int items = 0;
        int logicalCows = 0;
        int cowEntities = 0;
        int unprotectedMaxStack = 0;
        List<String> cows = new ArrayList<>();
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Item item) {
                items++;
                if (item.getItemStack().getItemMeta() instanceof BundleMeta) bundles++;
                add(ground, item.getItemStack());
            }
            if (entity instanceof Cow) {
                cowEntities++;
                cows.add(entity.getUniqueId().toString());
                Integer count = entity.getPersistentDataContainer().get(new NamespacedKey("react", "react-stack-count"), PersistentDataType.INTEGER);
                int represented = count == null ? 1 : count;
                logicalCows += represented;
                if (!entity.getUniqueId().equals(protectedCow)) unprotectedMaxStack = Math.max(unprotectedMaxStack, represented);
            }
        }
        Entity protectedEntity = Bukkit.getEntity(protectedCow);
        result.put("inventory", inventory);
        result.put("ground", ground);
        result.put("bundles", bundles);
        result.put("itemEntities", items);
        result.put("logicalCows", logicalCows);
        result.put("cowEntities", cowEntities);
        result.put("cowIds", cows);
        result.put("unprotectedMaxStack", unprotectedMaxStack);
        result.put("protectedCow", protectedCow.toString());
        result.put("protectedAlive", protectedEntity != null && protectedEntity.isValid());
        result.put("protectedClaim", protectedEntity != null && ReactProtection.isProtected(protectedEntity, ReactOperation.STACK));
        result.put("protectedCount", protectedEntity == null ? 0 : protectedEntity.getPersistentDataContainer().getOrDefault(new NamespacedKey("react", "react-stack-count"), PersistentDataType.INTEGER, 1));
        return result;
    }

    private void add(Map<String, Integer> totals, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return;
        if (stack.getItemMeta() instanceof BundleMeta bundle) {
            for (ItemStack item : bundle.getItems()) add(totals, item);
        } else totals.merge(stack.getType().name().toLowerCase(), stack.getAmount(), Integer::sum);
    }

    private void cleanup() {
        if (world == null) return;
        World home = Bukkit.getWorlds().getFirst();
        for (Player player : List.copyOf(world.getPlayers())) player.teleport(home.getSpawnLocation());
        if (!Bukkit.unloadWorld(world, false)) throw new IllegalStateException("Could not unload comparison world");
        world = null;
        actors.clear();
    }

    public void close() {
        cleanup();
        React.instance.setMonitoringOnly(originalMode);
    }
}
