package art.arcane.react.mod.cost;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.UnaryOperator;

/**
 * Bytes and packets sent to and received from players, per channel namespace. Vanilla packets count as
 * {@code minecraft} and custom payloads under their channel's namespace. Netty threads only add to counters; the server
 * thread drains them once a second.
 */
public final class NetworkTraffic {
    public static final String MINECRAFT = "minecraft";
    private static volatile boolean active;
    private static final ConcurrentHashMap<String, Channel> CHANNELS = new ConcurrentHashMap<>();
    private static final Channel VANILLA = channel(MINECRAFT);

    private NetworkTraffic() {
    }

    public static void active(boolean measuring) {
        if (measuring && !active) {
            for (Channel channel : CHANNELS.values()) {
                channel.reset();
            }
        }
        active = measuring;
    }

    /** {@code namespace} is null for vanilla packets. */
    public static void sent(String namespace, int bytes) {
        if (active) {
            Channel channel = namespace == null ? VANILLA : channel(namespace);
            channel.sentBytes.add(bytes);
            channel.sentPackets.increment();
        }
    }

    /** {@code namespace} is null for vanilla packets. */
    public static void received(String namespace, int bytes) {
        if (active) {
            Channel channel = namespace == null ? VANILLA : channel(namespace);
            channel.receivedBytes.add(bytes);
            channel.receivedPackets.increment();
        }
    }

    public static boolean active() {
        return active;
    }

    /** Per-second rates since the last drain, per owner; {@code owners} folds namespaces into the mods that own them. */
    public static Map<String, Rates> drain(double seconds, UnaryOperator<String> owners) {
        Map<String, Rates> rates = new TreeMap<>();
        double window = Math.max(seconds, 0.001D);
        for (Channel channel : CHANNELS.values()) {
            long sentBytes = channel.sentBytes.sumThenReset();
            long sentPackets = channel.sentPackets.sumThenReset();
            long receivedBytes = channel.receivedBytes.sumThenReset();
            long receivedPackets = channel.receivedPackets.sumThenReset();
            if (sentPackets == 0L && receivedPackets == 0L && !channel.namespace.equals(MINECRAFT)) {
                continue;
            }
            Rates next = new Rates(sentBytes / window, receivedBytes / window, sentPackets / window, receivedPackets / window);
            rates.merge(owners.apply(channel.namespace), next, Rates::plus);
        }
        return rates;
    }

    private static Channel channel(String namespace) {
        Channel channel = CHANNELS.get(namespace);
        return channel != null ? channel : CHANNELS.computeIfAbsent(namespace, Channel::new);
    }

    public record Rates(double sentBytes, double receivedBytes, double sentPackets, double receivedPackets) {
        public static final Rates NONE = new Rates(0D, 0D, 0D, 0D);

        Rates plus(Rates other) {
            return new Rates(sentBytes + other.sentBytes, receivedBytes + other.receivedBytes,
                    sentPackets + other.sentPackets, receivedPackets + other.receivedPackets);
        }
    }

    private static final class Channel {
        private final String namespace;
        private final LongAdder sentBytes = new LongAdder();
        private final LongAdder sentPackets = new LongAdder();
        private final LongAdder receivedBytes = new LongAdder();
        private final LongAdder receivedPackets = new LongAdder();

        private Channel(String namespace) {
            this.namespace = namespace;
        }

        private void reset() {
            sentBytes.reset();
            sentPackets.reset();
            receivedBytes.reset();
            receivedPackets.reset();
        }
    }
}
