package art.arcane.react.test;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.content.action.ActionPrewarmCriticalChunks;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import com.google.gson.Gson;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class AsyncPrewarmFixture {
    private static final String WEIGHT = "qa-prewarm-selection";
    private final JavaPlugin plugin;
    private ActionTicket<ActionPrewarmCriticalChunks.Params> ticket;
    private SampledChunk selection;
    private volatile Target target;
    private volatile Map<String, Object> inspection = Map.of();
    private String mode = "idle";

    public AsyncPrewarmFixture(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean execute(Player player, String[] args) {
        try {
            switch (args[1]) {
                case "prepare" -> select(unexplored(player.getWorld()));
                case "missing" -> begin(false, false, false);
                case "generate" -> begin(true, false, false);
                case "existing" -> {
                    Location location = player.getLocation();
                    select(new Target(player.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4));
                    begin(false, false, true);
                }
                case "cancel" -> {
                    select(unexplored(player.getWorld()));
                    begin(true, true, false);
                }
                case "snapshot" -> inspect();
                case "cleanup" -> close();
                default -> throw new IllegalArgumentException("Unknown prewarm command");
            }
            player.sendMessage("REACT_PREWARM " + args[1] + " " + new Gson().toJson(snapshot()));
        } catch (Exception failure) {
            plugin.getLogger().log(Level.SEVERE, "Prewarm fixture command failed", failure);
            player.sendMessage("REACT_PREWARM ERROR " + failure.getMessage());
        }
        return true;
    }

    private Target unexplored(World world) {
        int coordinate = 4096 + Math.floorMod(UUID.randomUUID().hashCode(), 65536);
        return new Target(world, coordinate, coordinate);
    }

    private void select(Target next) {
        if (ticket != null && !ticket.isDone()) throw new IllegalStateException("Prewarm fixture already active");
        clearSelection();
        ticket = null;
        inspection = Map.of();
        target = next;
        mode = "prepared";
        inspect();
    }

    private void begin(boolean generate, boolean cancel, boolean playerChunks) {
        if (target == null || ticket != null && !ticket.isDone()) throw new IllegalStateException("Prewarm fixture is not ready");
        Action<ActionPrewarmCriticalChunks.Params> action = React.action(ActionPrewarmCriticalChunks.ID);
        if (action == null || !action.isEnabled()) throw new IllegalStateException("Prewarm action unavailable");
        if (!playerChunks) {
            selection = React.controller(ObserverController.class).getSampled().getWorld(target.world()).getChunk(target.x(), target.z());
            selection.get(WEIGHT).set(1_000_000_000_000D);
        }
        ticket = action.create(ActionPrewarmCriticalChunks.Params.builder()
                .world(WorldIdentity.serialize(target.world())).maxChunks(1).neighborRadius(0)
                .includePlayerChunks(playerChunks).playerChunkRadius(0).generateMissingChunks(generate).touchChunkSnapshot(true).build());
        mode = cancel ? "cancelled" : generate ? "generate" : "existing-only";
        if (cancel) {
            ticket.start();
            action.workOn(ticket);
            ticket.fail(new IllegalStateException("Prewarm fixture cancelled after dispatch"));
        } else {
            ticket.queue();
        }
        inspect();
    }

    private void inspect() {
        Target captured = target;
        if (captured == null) return;
        if (!J.runChunk(captured.world(), captured.x(), captured.z(), () -> {
            if (target != captured) return;
            try {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("generated", captured.world().isChunkGenerated(captured.x(), captured.z()));
                result.put("loaded", captured.world().isChunkLoaded(captured.x(), captured.z()));
                result.put("ready", true);
                if (target == captured) inspection = Map.copyOf(result);
            } catch (Exception failure) {
                plugin.getLogger().log(Level.SEVERE, "Prewarm fixture owner inspection failed", failure);
                if (target == captured) inspection = Map.of("ready", false, "error", failure.toString());
            }
        })) {
            throw new IllegalStateException("Prewarm inspection scheduling rejected");
        }
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("mode", mode);
        value.put("coverage", "Real chunk loading and player-index selection; controlled hotspot selection for remote chunks");
        value.put("inspection", inspection);
        Target captured = target;
        if (captured != null) {
            value.put("world", WorldIdentity.serialize(captured.world()));
            value.put("chunkX", captured.x());
            value.put("chunkZ", captured.z());
        }
        if (ticket != null) {
            value.put("playerChunks", ticket.getParams().isIncludePlayerChunks());
            value.put("done", ticket.isDone());
            value.put("failed", ticket.isFailed());
            value.put("warmed", ticket.getParams().getChunksWarmedAtomic().get());
            value.put("loaded", ticket.getParams().getChunksLoadedAtomic().get());
            value.put("inFlight", ticket.getParams().getInFlightChunks().get());
            value.put("processed", ticket.getParams().getChunksProcessedAtomic().get());
        }
        return value;
    }

    private void clearSelection() {
        if (selection != null) {
            selection.getValues().remove(WEIGHT);
            selection = null;
        }
    }

    public void close() {
        if (ticket != null && !ticket.isDone()) ticket.fail(new IllegalStateException("Prewarm fixture cleanup"));
        clearSelection();
        target = null;
        mode = "closed";
    }

    private record Target(World world, int x, int z) {
    }
}
