/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import org.bukkit.Location;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerPortalEvent;

import java.util.concurrent.atomic.AtomicBoolean;

@art.arcane.react.util.config.ConfigDescription("Configuration for Incident Mode feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureIncidentMode extends ReactFeature implements Listener {
    public static final String ID = "incident-mode";
    @art.arcane.react.util.config.ConfigDoc(value = "Main evaluation interval for incident mode in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
    private int tickIntervalMS = 1000;
    @art.arcane.react.util.config.ConfigDoc(value = "Trigger threshold for enter incident score in incident mode.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
    private double enterIncidentScore = 58;
    @art.arcane.react.util.config.ConfigDoc(value = "Trigger threshold for exit incident score in incident mode.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
    private double exitIncidentScore = 35;
    @art.arcane.react.util.config.ConfigDoc(value = "Tick-time threshold for enter in incident mode (milliseconds).", impact = "Higher values delay activation or exit; lower values make this threshold easier to cross.")
    private double enterTickMS = 60;
    @art.arcane.react.util.config.ConfigDoc(value = "Tick-time threshold for exit in incident mode (milliseconds).", impact = "Higher values delay activation or exit; lower values make this threshold easier to cross.")
    private double exitTickMS = 46;
    @art.arcane.react.util.config.ConfigDoc(value = "Minimum incident duration ms required by incident mode.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
    private int minimumIncidentDurationMS = 8000;
    @art.arcane.react.util.config.ConfigDoc(value = "Rolling window length for rate checks (milliseconds).", impact = "Longer windows smooth bursts but react slower; shorter windows react faster but are more sensitive.")
    private int rateWindowMS = 1000;
    @art.arcane.react.util.config.ConfigDoc(value = "Maximum spawner spawns allowed per window in incident mode.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxSpawnerSpawnsPerWindow = 28;
    @art.arcane.react.util.config.ConfigDoc(value = "Maximum natural spawns allowed per window in incident mode.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxNaturalSpawnsPerWindow = 70;
    @art.arcane.react.util.config.ConfigDoc(value = "Maximum portal events allowed per window in incident mode.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxPortalEventsPerWindow = 18;
    @art.arcane.react.util.config.ConfigDoc(value = "Maximum hopper moves allowed per window in incident mode.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxHopperMovesPerWindow = 120;
    @art.arcane.react.util.config.ConfigDoc(value = "Maximum redstone transitions allowed per window in incident mode.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxRedstoneTransitionsPerWindow = 220;
    @art.arcane.react.util.config.ConfigDoc(value = "Bypasses incident mode handling for bypass near players.", impact = "Enable this to skip enforcement in matching situations; disable it for strict handling.")
    private boolean bypassNearPlayers = true;
    @art.arcane.react.util.config.ConfigDoc(value = "Bypass player radius used by incident mode (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
    private double bypassPlayerRadius = 14;
    @art.arcane.react.util.config.ConfigDoc(value = "Enables extra logging for verbose transitions in incident mode.", impact = "Enable for diagnostics; disable to reduce chat or log noise.")
    private boolean verboseTransitions = true;
    private transient volatile boolean incident;
    private transient volatile long incidentSince;
    private transient long windowStartMS;
    private transient int spawnerSpawns;
    private transient int naturalSpawns;
    private transient int portalEvents;
    private transient int hopperMoves;
    private transient int redstoneTransitions;
    private transient final AtomicBoolean evaluationQueued = new AtomicBoolean(false);

    public FeatureIncidentMode() {
        super(ID);
    }

    @Override
    public void onActivate() {
        long now = System.currentTimeMillis();
        incident = false;
        incidentSince = 0L;
        windowStartMS = now;
        spawnerSpawns = 0;
        naturalSpawns = 0;
        portalEvents = 0;
        hopperMoves = 0;
        redstoneTransitions = 0;
        evaluationQueued.set(false);
    }

    @Override
    public void onDeactivate() {
        incident = false;
    }

    @Override
    public int getTickInterval() {
        return tickIntervalMS;
    }

    @Override
    public void onTick() {
        if (!evaluationQueued.compareAndSet(false, true)) {
            return;
        }

        art.arcane.react.util.scheduling.J.s(() -> {
            try {
                evaluateIncident();
            } finally {
                evaluationQueued.set(false);
            }
        });
    }

    private void evaluateIncident() {
        double tickMS = sample(SamplerTickTime.ID);
        long now = System.currentTimeMillis();

        if (!incident) {
            double incidentScore = sample(SamplerIncidentScore.ID);
            if (incidentScore >= enterIncidentScore || tickMS >= enterTickMS) {
                incident = true;
                incidentSince = now;
                if (verboseTransitions) {
                    React.warn("Incident mode enabled (score " + String.format("%.1f", incidentScore) + ", tick " + String.format("%.1f", tickMS) + "ms)");
                }
            }
            return;
        }

        if (now - incidentSince < minimumIncidentDurationMS || tickMS > exitTickMS) {
            return;
        }

        double incidentScore = sample(SamplerIncidentScore.ID);
        if (incidentScore <= exitIncidentScore) {
            incident = false;
            if (verboseTransitions) {
                React.info("Incident mode disabled (score " + String.format("%.1f", incidentScore) + ", tick " + String.format("%.1f", tickMS) + "ms)");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(CreatureSpawnEvent event) {
        if (!incident) {
            return;
        }

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        boolean spawner = reason == CreatureSpawnEvent.SpawnReason.SPAWNER || "TRIAL_SPAWNER".equals(reason.name());
        boolean natural = switch (reason) {
            case NATURAL, CHUNK_GEN, NETHER_PORTAL, REINFORCEMENTS, JOCKEY, PATROL, RAID -> true;
            default -> false;
        };

        long now = System.currentTimeMillis();
        rolloverWindow(now);

        if (spawner) {
            if (++spawnerSpawns > maxSpawnerSpawnsPerWindow) {
                event.setCancelled(true);
            }
            return;
        }

        if (natural && ++naturalSpawns > maxNaturalSpawnsPerWindow) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(PlayerPortalEvent event) {
        if (!incident) {
            return;
        }

        Location location = event.getTo() == null ? event.getPlayer().getLocation() : event.getTo();
        if (shouldBypass(location)) {
            return;
        }

        long now = System.currentTimeMillis();
        rolloverWindow(now);
        if (++portalEvents > maxPortalEventsPerWindow) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(EntityPortalEvent event) {
        if (!incident) {
            return;
        }

        Location location = event.getTo() == null ? event.getEntity().getLocation() : event.getTo();
        if (shouldBypass(location)) {
            return;
        }

        long now = System.currentTimeMillis();
        rolloverWindow(now);
        if (++portalEvents > maxPortalEventsPerWindow) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void on(InventoryMoveItemEvent event) {
        if (!incident) {
            return;
        }

        Location location = resolveHopperLocation(event);
        if (location == null || shouldBypass(location)) {
            return;
        }

        long now = System.currentTimeMillis();
        rolloverWindow(now);
        if (++hopperMoves > maxHopperMovesPerWindow) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(BlockRedstoneEvent event) {
        if (!incident || event.getOldCurrent() == event.getNewCurrent()) {
            return;
        }

        Location location = event.getBlock().getLocation();
        if (shouldBypass(location)) {
            return;
        }

        long now = System.currentTimeMillis();
        rolloverWindow(now);
        if (++redstoneTransitions > maxRedstoneTransitionsPerWindow) {
            event.setNewCurrent(event.getOldCurrent());
        }
    }

    private void rolloverWindow(long now) {
        if (now - windowStartMS <= rateWindowMS) {
            return;
        }

        windowStartMS = now;
        spawnerSpawns = 0;
        naturalSpawns = 0;
        portalEvents = 0;
        hopperMoves = 0;
        redstoneTransitions = 0;
    }

    private boolean shouldBypass(Location location) {
        return bypassNearPlayers && location != null && React.hasNearbyPlayer(location, bypassPlayerRadius);
    }

    private Location resolveHopperLocation(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof Hopper source) {
            return source.getBlock().getLocation();
        }

        if (event.getDestination().getHolder() instanceof Hopper destination) {
            return destination.getBlock().getLocation();
        }

        return null;
    }

    private double sample(String id) {
        var sampler = React.sampler(id);
        return sampler == null ? 0D : sampler.sample();
    }
}
