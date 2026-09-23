package art.arcane.react.test;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class GameplayObserver extends JavaPlugin implements Listener {
    private ComparisonFixture comparison;
    private long breaks;
    private long placements;
    private long moves;

    @Override
    public void onEnable() {
        try {
            if (!Files.readAllLines(Path.of(".server-source")).contains("isolated=true")
                    || !Bukkit.getIp().equals("127.0.0.1") || Bukkit.getOnlineMode()) {
                throw new IllegalStateException("Observer requires an isolated offline loopback instance");
            }
            Bukkit.getPluginManager().registerEvents(this, this);
            comparison = new ComparisonFixture(this);
        } catch (Exception failure) {
            getLogger().log(Level.SEVERE, "Gameplay observer refused startup", failure);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (comparison != null) comparison.close();
    }

    private boolean actor(Player player) {
        return !player.isOp() && player.getName().startsWith("RQA");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (actor(event.getPlayer())) breaks++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (actor(event.getPlayer())) placements++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (actor(event.getPlayer()) && event.hasChangedPosition()) moves++;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) return false;
        if (args.length > 1 && args[0].equals("comparison")) return comparison.execute(player, args);
        if (args.length != 1 || !args[0].matches("[a-z0-9]+")) return false;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("observedAt", System.currentTimeMillis());
        snapshot.put("server", Bukkit.getName());
        snapshot.put("online", Bukkit.getOnlinePlayers().size());
        snapshot.put("actors", Bukkit.getOnlinePlayers().stream().filter(this::actor).map(Player::getName).toList());
        snapshot.put("breaks", breaks);
        snapshot.put("placements", placements);
        snapshot.put("moves", moves);
        Map<String, Object> metrics = new LinkedHashMap<>();
        for (String id : List.of("players", "chunks", "entities", "tick-time")) {
            Sampler sampler = React.sampler(id);
            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("registered", sampler != null);
            if (sampler != null) {
                double value = sampler.sample();
                boolean available = sampler.isSampleAvailable();
                metric.put("available", available);
                metric.put("valid", Double.isFinite(value) && value >= 0);
                if (available && Double.isFinite(value)) metric.put("value", value);
            }
            metrics.put(id, metric);
        }
        snapshot.put("metrics", metrics);
        sender.sendMessage("REACT_QA " + args[0] + " " + new Gson().toJson(snapshot));
        return true;
    }
}
