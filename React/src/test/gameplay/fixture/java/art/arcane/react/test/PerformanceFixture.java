package art.arcane.react.test;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.action.ReactAction;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.action.ActionCollectGarbage;
import art.arcane.react.content.action.ActionHopperNetworkNormalize;
import art.arcane.react.content.action.ActionIncidentPlaybook;
import art.arcane.react.content.action.ActionPrewarmCriticalChunks;
import art.arcane.react.content.action.ActionQuarantineHotChunks;
import art.arcane.react.content.action.ActionTrimEntitiesByAgePriority;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.content.sampler.SamplerHopperUpdates;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledWorld;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.registry.Registry;
import art.arcane.react.util.project.world.NearbyEntitySampler;
import com.google.gson.Gson;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class PerformanceFixture {
    private final JavaPlugin plugin;
    private final List<Entity> spawned = new ArrayList<>();
    private final Map<Location, BlockData> blocks = new LinkedHashMap<>();
    private final Map<String, Action<?>> originalActions = new LinkedHashMap<>();
    private final Map<String, ProbeAction> probes = new LinkedHashMap<>();
    private final List<Integer> maps = new ArrayList<>();
    private final List<UUID> stands = new ArrayList<>();
    private final Set<UUID> sampled = new HashSet<>();
    private ActionTicket<ActionIncidentPlaybook.Params> ticket;
    private SampledWorld evidence;
    private boolean active;
    private boolean controlledPressure;

    public PerformanceFixture(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean execute(Player player, String[] args) {
        try {
            switch (args[1]) {
                case "ready", "snapshot" -> { }
                case "begin" -> begin(player);
                case "release" -> release();
                case "cleanup" -> close();
                default -> throw new IllegalArgumentException("Unknown performance command");
            }
            player.sendMessage("REACT_PERFORMANCE " + args[1] + " " + new Gson().toJson(snapshot()));
        } catch (Exception failure) {
            plugin.getLogger().log(Level.SEVERE, "Performance fixture command failed", failure);
            player.sendMessage("REACT_PERFORMANCE ERROR " + failure.getMessage());
        }
        return true;
    }

    private void begin(Player player) {
        if (active) throw new IllegalStateException("Performance fixture already active");
        controlledPressure = pressureAvailable();
        maps.clear();
        stands.clear();
        sampled.clear();
        probes.clear();
        active = true;
        spawnEntitySamples(player);
        createMapWall(player);
        installActionProbes();
        evidence = new SampledWorld(UUID.randomUUID(), "qa:performance");
        evidence.getChunk(0, 0).get(SamplerHopperUpdates.ID).set(10000D);
        evidence.getChunk(0, 0).gauge(SamplerEntities.ID).set(100D);
        React.controller(ObserverController.class).getSampled().getWorlds().put(evidence.getWorldId(), evidence);
        Action<ActionIncidentPlaybook.Params> playbook = React.action(ActionIncidentPlaybook.ID);
        if (playbook == null) throw new IllegalStateException("Incident playbook unavailable");
        ticket = playbook.create(ActionIncidentPlaybook.Params.builder()
                .world(evidence.getWorldKey())
                .minimumIncidentScore(controlledPressure ? 0D : 35D)
                .minimumTickMS(controlledPressure ? 1D : 48D)
                .recheckDelayMS(1000L)
                .build());
        ticket.queue();
    }

    private void spawnEntitySamples(Player player) {
        Location origin = player.getLocation();
        for (int index = 0; index < 3; index++) {
            ArmorStand stand = origin.getWorld().spawn(origin.clone().add(0.25D, 0D, 0.25D), ArmorStand.class, entity -> {
                entity.setGravity(false);
                entity.setInvulnerable(true);
                entity.setCustomName("React performance fixture");
                entity.setPersistent(false);
            });
            spawned.add(stand);
            stands.add(stand.getUniqueId());
        }
        NearbyEntitySampler sampler = React.controller(EntityController.class).getNearbyEntitySampler();
        for (int index = 0; index < 16; index++) {
            for (Entity entity : sampler.sample(player, new NearbyEntitySampler.Request("gameplay", 2D, 3D, 1))) {
                sampled.add(entity.getUniqueId());
            }
        }
        if (!sampled.containsAll(stands)) throw new IllegalStateException("Bounded entity rotation missed nearby fixture entities");
    }

    private void createMapWall(Player player) {
        World world = player.getWorld();
        Location origin = player.getLocation();
        int x = origin.getBlockX();
        int y = origin.getBlockY() + 1;
        int z = origin.getBlockZ() + 5;
        MapController controller = React.controller(MapController.class);
        Sampler renderer = React.sampler(SamplerTickTime.ID);
        for (int index = 0; index < 3; index++) {
            Location backing = new Location(world, x + index, y, z);
            blocks.put(backing, backing.getBlock().getBlockData());
            backing.getBlock().setType(Material.STONE, false);
            ItemStack item = controller.createMap(world, renderer);
            MapMeta meta = (MapMeta) item.getItemMeta();
            maps.add(meta.getMapView().getId());
            ItemFrame frame = world.spawn(new Location(world, x + index, y, z - 1), ItemFrame.class, entity -> {
                entity.setFacingDirection(BlockFace.NORTH, true);
                entity.setFixed(true);
                entity.setInvulnerable(true);
                entity.setPersistent(false);
                entity.setItem(item);
            });
            spawned.add(frame);
            controller.scheduleFrameRefresh(frame);
        }
    }

    private void installActionProbes() {
        Registry<Action<?>> registry = React.controller(ActionController.class).getActions();
        for (String id : List.of(ActionHopperNetworkNormalize.ID, ActionTrimEntitiesByAgePriority.ID,
                ActionQuarantineHotChunks.ID, ActionPrewarmCriticalChunks.ID, ActionCollectGarbage.ID)) {
            Action<?> original = registry.unregister(id);
            if (original == null) throw new IllegalStateException("Missing action " + id);
            originalActions.put(id, original);
            ProbeAction probe = new ProbeAction(id);
            probes.put(id, probe);
            if (registry.register(probe) != probe) throw new IllegalStateException("Cannot install action probe " + id);
        }
    }

    private void release() {
        if (ticket == null || ticket.getParams().getActiveChild() == null || ticket.isDone()) {
            throw new IllegalStateException("Playbook has not started its held child");
        }
        ticket.getParams().setMinimumIncidentScore(100000D).setMinimumTickMS(100000D);
        probes.get(ActionHopperNetworkNormalize.ID).released = true;
    }

    private boolean pressureAvailable() {
        return samplerAvailable(SamplerIncidentScore.ID) | samplerAvailable(SamplerTickTime.ID);
    }

    private boolean samplerAvailable(String id) {
        Sampler sampler = React.sampler(id);
        if (sampler == null) return false;
        double value = sampler.sample();
        return sampler.isSampleAvailable() && Double.isFinite(value);
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("active", active);
        snapshot.put("pressureAvailable", pressureAvailable());
        snapshot.put("incidentAvailable", samplerAvailable(SamplerIncidentScore.ID));
        snapshot.put("tickAvailable", samplerAvailable(SamplerTickTime.ID));
        snapshot.put("controlledPressure", controlledPressure);
        snapshot.put("folia", J.isFoliaThreading());
        snapshot.put("coverage", "Controlled playbook evidence and child lifecycle; real entity rotation and map packets");
        snapshot.put("mapIds", maps);
        snapshot.put("standIds", stands);
        snapshot.put("sampledIds", sampled);
        Map<String, Integer> calls = new LinkedHashMap<>();
        probes.forEach((id, probe) -> calls.put(id, probe.calls));
        snapshot.put("actionCalls", calls);
        if (ticket != null) {
            snapshot.put("done", ticket.isDone());
            snapshot.put("failed", ticket.isFailed());
            snapshot.put("completedActions", ticket.getCount());
            ActionTicket<?> child = ticket.getParams().getActiveChild();
            snapshot.put("childRunning", child != null && child.isRunning());
            snapshot.put("childDone", child != null && child.isDone());
        }
        return snapshot;
    }

    public void close() {
        if (ticket != null && !ticket.isDone()) ticket.fail(new IllegalStateException("Performance fixture cleanup"));
        if (!originalActions.isEmpty()) {
            Registry<Action<?>> registry = React.controller(ActionController.class).getActions();
            for (Map.Entry<String, Action<?>> entry : originalActions.entrySet()) {
                registry.unregister(entry.getKey());
                registry.register(entry.getValue());
            }
        }
        originalActions.clear();
        if (evidence != null) {
            React.controller(ObserverController.class).getSampled().getWorlds().remove(evidence.getWorldId(), evidence);
            evidence = null;
        }
        for (Entity entity : spawned) J.runEntity(entity, entity::remove);
        for (Map.Entry<Location, BlockData> entry : blocks.entrySet()) {
            Location location = entry.getKey();
            BlockData data = entry.getValue();
            J.runChunk(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4,
                    () -> location.getBlock().setBlockData(data, false));
        }
        spawned.clear();
        blocks.clear();
        active = false;
    }

    private static final class ProbeAction extends ReactAction<ActionParams> {
        private volatile int calls;
        private volatile boolean released;

        private ProbeAction(String id) {
            super(id);
        }

        @Override
        public void loadConfiguration() {
        }

        @Override
        public void workOn(ActionTicket<ActionParams> child) {
            calls++;
            if (!ActionHopperNetworkNormalize.ID.equals(getId())) {
                throw new IllegalStateException("Unexpected mitigation " + getId());
            }
            if (released) child.complete();
        }

        @Override
        public ActionParams getDefaultParams() {
            return ActionHopperNetworkNormalize.Params.builder().build();
        }

        @Override
        public void onInit() {
        }
    }
}
