package art.arcane.react.mod;

import art.arcane.react.core.controller.SampleController;
import art.arcane.react.mod.cost.ChunkLoaderCensus;
import art.arcane.react.mod.cost.CostRegistry;
import art.arcane.react.mod.cost.NetworkTraffic;
import art.arcane.react.platform.cost.OwnerGauge;
import art.arcane.react.platform.cost.OwnerGaugeSampler;
import net.minecraft.server.MinecraftServer;

import java.util.HashSet;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Per-mod chunk loaders and network traffic: a chunk ticket census every five seconds and the traffic counters drained
 * every second, each owner getting its samplers the first time it shows up.
 */
final class ModOwnerGauges {
    private static final int CENSUS_INTERVAL = 5;
    private final MinecraftServer server;
    private final Supplier<SampleController> samples;
    private final ChunkLoaderCensus census = new ChunkLoaderCensus();
    private final Set<String> registered = new HashSet<>();
    private volatile Map<String, NetworkTraffic.Rates> traffic = Map.of();
    private long drainedAtNanos;
    private int untilCensus;

    ModOwnerGauges(MinecraftServer server, Supplier<SampleController> samples) {
        this.server = server;
        this.samples = samples;
    }

    void start() {
        drainedAtNanos = System.nanoTime();
        NetworkTraffic.active(true);
    }

    void stop() {
        NetworkTraffic.active(false);
    }

    /** Server thread, once a second. */
    void publish() {
        long now = System.nanoTime();
        traffic = NetworkTraffic.drain((now - drainedAtNanos) / 1_000_000_000D, CostRegistry::ownerOfNamespace);
        drainedAtNanos = now;
        if (--untilCensus <= 0) {
            untilCensus = CENSUS_INTERVAL;
            census.run(server);
        }
        SampleController controller = samples.get();
        if (controller == null) {
            return;
        }
        for (String owner : census.counts().keySet()) {
            register(controller, OwnerGauge.CHUNK_LOADERS, owner);
        }
        for (String owner : traffic.keySet()) {
            register(controller, OwnerGauge.NETWORK_OUT, owner);
            register(controller, OwnerGauge.NETWORK_IN, owner);
            register(controller, OwnerGauge.PACKETS_OUT, owner);
            register(controller, OwnerGauge.PACKETS_IN, owner);
        }
    }

    /** Chunks held by every owner in the latest census. */
    OptionalLong chunkTicketTotal() {
        return OptionalLong.of(census.total());
    }

    double value(OwnerGauge gauge, String owner) {
        NetworkTraffic.Rates rates = traffic.getOrDefault(owner, NetworkTraffic.Rates.NONE);
        return switch (gauge) {
            case NETWORK_OUT -> rates.sentBytes();
            case NETWORK_IN -> rates.receivedBytes();
            case PACKETS_OUT -> rates.sentPackets();
            case PACKETS_IN -> rates.receivedPackets();
            case CHUNK_LOADERS -> census.count(owner);
        };
    }

    private void register(SampleController controller, OwnerGauge gauge, String owner) {
        if (owner.isBlank() || !registered.add(gauge.samplerId(owner))) {
            return;
        }
        controller.registerSampler(new OwnerGaugeSampler(gauge, owner, CostRegistry.displayName(owner), (String id) -> value(gauge, id)));
    }
}
