package art.arcane.react.test;

import art.arcane.react.model.ReactEntity;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class EntityLifecycleFixture implements Listener {
    private static final String PREFIX = "react-lifecycle-";
    private final JavaPlugin plugin;
    private final Map<String, Map<String, Object>> loaded = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> unloaded = new ConcurrentHashMap<>();

    public EntityLifecycleFixture(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean execute(Player player, String[] args) {
        try {
            switch (args[1]) {
                case "prepare" -> prepare(player);
                case "pause" -> pause(player);
                case "snapshot" -> { }
                default -> throw new IllegalArgumentException("Unknown entity lifecycle command");
            }
            player.sendMessage("REACT_LIFECYCLE " + args[1] + " " + new Gson().toJson(
                    Map.of("loaded", loaded, "unloaded", unloaded)));
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Entity lifecycle fixture command failed", failure);
            player.sendMessage("REACT_LIFECYCLE ERROR " + failure.getMessage());
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            record(entity, unloaded);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            record(entity, loaded);
        }
    }

    private void prepare(Player player) {
        loaded.clear();
        unloaded.clear();
        Location origin = player.getLocation();
        int index = 0;
        for (boolean ai : new boolean[]{true, false}) {
            for (boolean aware : new boolean[]{true, false}) {
                String tag = PREFIX + (ai ? "1" : "0") + (aware ? "1" : "0");
                Cow mob = player.getWorld().spawn(origin.clone().add(++index, 0, 0), Cow.class);
                mob.addScoreboardTag(tag);
                mob.setPersistent(true);
                mob.setRemoveWhenFarAway(false);
                mob.setGravity(false);
                mob.setAI(ai);
                mob.setAware(aware);
                pause(mob);
                record(mob, loaded);
            }
        }
    }

    private void pause(Player player) {
        for (Entity entity : player.getNearbyEntities(12, 12, 12)) {
            if (entity instanceof Mob mob && tag(entity) != null) {
                pause(mob);
                record(mob, loaded);
            }
        }
    }

    private void pause(Mob mob) {
        ReactEntity.requestPause(mob, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
        ReactEntity.requestPause(mob, ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE);
        ReactEntity.requestDoze(mob);
    }

    private void record(Entity entity, Map<String, Map<String, Object>> destination) {
        String tag = tag(entity);
        if (!(entity instanceof Mob mob) || tag == null) {
            return;
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("ai", mob.hasAI());
        state.put("aware", mob.isAware());
        state.put("paused", ReactEntity.isPaused(mob));
        state.put("dozing", ReactEntity.isDozing(mob));
        destination.put(tag, Map.copyOf(state));
    }

    private String tag(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith(PREFIX)) {
                return tag;
            }
        }
        return null;
    }
}
