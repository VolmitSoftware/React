package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerRedstoneEventSpan;
import art.arcane.react.core.controller.IncidentController;
import art.arcane.react.core.incident.IncidentAction;
import art.arcane.react.core.incident.IncidentEvidence;
import art.arcane.react.core.incident.IncidentLocation;
import art.arcane.react.core.incident.IncidentRecord;
import art.arcane.react.model.CircuitObservation;
import art.arcane.react.model.CircuitServer;
import art.arcane.react.model.CircuitSnapshot;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@art.arcane.react.util.project.config.ConfigDescription("Tracks adjacent redstone activity components and temporarily throttles the busiest component during redstone pressure.")
public class FeatureCircuitManager extends ReactFeature implements Listener {
  public static final String ID = "circuit-manager";

  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum global redstone event span allowed before circuit throttling.", impact = "Higher values tolerate longer redstone event bursts; lower values throttle the busiest observed activity component sooner.")
  private double maxCircuitMS = 15D;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Duration of a circuit throttle in milliseconds.", impact = "Longer durations suppress a problematic component for longer; shorter durations retry it sooner.")
  private int throttleDurationMS = 10000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Inactive circuit activity retention in milliseconds.", impact = "Longer retention links slower multi-stage circuits across more time and uses more memory; shorter retention forgets inactive topology sooner.")
  private int activityRetentionMS = 15000;

  private transient final AtomicLong evaluationQueuedGeneration = new AtomicLong(-1L);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong();
  private transient volatile CircuitServer circuitServer;
  private transient volatile boolean active;

  public FeatureCircuitManager() {
    super(ID);
  }

  @Override
  public void onActivate() {
    lifecycleGeneration.incrementAndGet();
    circuitServer = new CircuitServer();
    evaluationQueuedGeneration.set(-1L);
    active = true;
  }

  @Override
  public void onDeactivate() {
    active = false;
    lifecycleGeneration.incrementAndGet();
    evaluationQueuedGeneration.set(-1L);
    circuitServer = null;
  }

  @Override
  public int getTickInterval() {
    return 1000;
  }

  @Override
  public void onTick() {
    if (!active) {
      return;
    }
    long generation = lifecycleGeneration.get();
    if (!evaluationQueuedGeneration.compareAndSet(-1L, generation)) {
      return;
    }
    try {
      J.s(() -> {
        try {
          if (active && lifecycleGeneration.get() == generation) {
            evaluate();
          }
        } finally {
          evaluationQueuedGeneration.compareAndSet(generation, -1L);
        }
      });
    } catch (RuntimeException | Error failure) {
      evaluationQueuedGeneration.compareAndSet(generation, -1L);
      throw failure;
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent event) {
    CircuitServer server = circuitServer;
    if (!active || server == null) {
      return;
    }
    server.remove(event.getBlock(), System.currentTimeMillis());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockPistonExtendEvent event) {
    CircuitObservation observation = observe(event.getBlock());
    if (observation != null && observation.blocked()) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockPistonRetractEvent event) {
    CircuitObservation observation = observe(event.getBlock());
    if (observation != null && observation.blocked()) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockRedstoneEvent event) {
    if (event.getOldCurrent() == event.getNewCurrent()) {
      return;
    }
    CircuitObservation observation = observe(event.getBlock());
    if (observation != null && observation.blocked()) {
      event.setNewCurrent(event.getOldCurrent());
    }
  }

  private CircuitObservation observe(Block block) {
    CircuitServer server = circuitServer;
    if (!active || server == null) {
      return null;
    }
    return server.event(block, System.currentTimeMillis());
  }

  private void evaluate() {
    CircuitServer server = circuitServer;
    if (server == null) {
      return;
    }
    long now = System.currentTimeMillis();
    server.rollWindow(now, Math.max(1000L, activityRetentionMS));
    double eventSpanMS = sample(SamplerRedstoneEventSpan.ID);
    double threshold = Double.isFinite(maxCircuitMS) ? Math.max(0D, maxCircuitMS) : 15D;
    if (!Double.isFinite(eventSpanMS) || eventSpanMS <= threshold) {
      return;
    }
    CircuitSnapshot circuit = server.throttleWorst(now, Math.max(1L, throttleDurationMS));
    if (circuit == null) {
      return;
    }
    recordThrottle(circuit, eventSpanMS, threshold, now);
    React.warn(
        "Throttling redstone activity component " + circuit.circuitId()
            + " in " + circuit.world()
            + " near " + circuit.x() + "," + circuit.y() + "," + circuit.z()
            + " (" + circuit.events() + " events, " + circuit.nodes() + " active nodes, "
            + format(eventSpanMS) + "ms global event span)"
    );
  }

  private void recordThrottle(CircuitSnapshot circuit, double eventSpanMS, double threshold, long now) {
    IncidentController controller = React.controller(IncidentController.class);
    if (controller == null) {
      return;
    }
    double normalizationMaximum = Math.max(threshold + 1D, threshold * 2D);
    double pressure = Math.max(0D, Math.min(1D, (eventSpanMS - threshold) / (normalizationMaximum - threshold)));
    long duration = Math.max(1L, circuit.blockedUntilMs() - now);
    controller.record(new IncidentRecord(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        "REDSTONE_CIRCUIT",
        "THROTTLED",
        eventSpanMS >= normalizationMaximum ? "CRITICAL" : "WARNING",
        now,
        now,
        ID,
        "Redstone activity component throttled",
        "React temporarily blocked the busiest observed adjacent redstone activity component.",
        "The global redstone event span reached " + format(eventSpanMS) + " ms, above the "
            + format(threshold) + " ms threshold. This component produced " + circuit.events()
            + " observed events in the current one-second attribution window.",
        new IncidentLocation(
            circuit.worldId(),
            circuit.world(),
            circuit.x(),
            circuit.y(),
            circuit.z()
        ),
        List.of(
            new IncidentEvidence(
                SamplerRedstoneEventSpan.ID,
                "Redstone Event Span",
                true,
                eventSpanMS,
                format(eventSpanMS) + " ms",
                pressure,
                0D,
                0D,
                threshold,
                normalizationMaximum
            ),
            new IncidentEvidence(
                "circuit-events-per-window",
                "Component Events",
                true,
                circuit.events(),
                circuit.events() + " events/s",
                0D,
                0D,
                0D,
                0D,
                Math.max(1D, circuit.events())
            )
        ),
        List.of(new IncidentAction(
            "circuit-throttle",
            "Temporary circuit throttle",
            "ACTIVE",
            "Redstone transitions and piston movement are blocked for " + duration + " ms.",
            now
        )),
        Map.of(
            "circuitId", Long.toString(circuit.circuitId()),
            "activeNodes", Integer.toString(circuit.nodes()),
            "eventsInWindow", Integer.toString(circuit.events()),
            "bounds", circuit.minX() + "," + circuit.minY() + "," + circuit.minZ()
                + " to " + circuit.maxX() + "," + circuit.maxY() + "," + circuit.maxZ(),
            "trackingModel", "adjacent observed redstone activity",
            "throttleDurationMs", Long.toString(duration)
        )
    ));
  }

  private String format(double value) {
    return String.format(Locale.ROOT, "%.1f", value);
  }
}
