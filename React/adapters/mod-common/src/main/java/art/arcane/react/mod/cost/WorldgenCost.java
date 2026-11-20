package art.arcane.react.mod.cost;

import art.arcane.react.mod.VanillaApi;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.LongAdder;

/**
 * World generation cost on the generator's worker threads. Placed features, structures and carvers are charged to the
 * mod that owns their registry namespace through an identity index built once per server, so a hook pays one map
 * read and two clock reads. Whole chunk steps are timed separately as a breakdown.
 */
public final class WorldgenCost {
    private static final Set<String> REGISTRIES = Set.of(
            "worldgen/placed_feature", "worldgen/structure", "worldgen/configured_carver", "worldgen/carver");
    private static final LongAdder[] STEP_NANOS = new LongAdder[WorldgenStep.values().length];
    private static final CostBucket UNKNOWN = CostRegistry.bucket(ModOwners.UNKNOWN, CostKind.WORLDGEN);
    private static volatile Map<Object, CostBucket> index = Map.of();
    private static volatile Set<String> owners = Set.of();

    static {
        for (int step = 0; step < STEP_NANOS.length; step++) {
            STEP_NANOS[step] = new LongAdder();
        }
    }

    private WorldgenCost() {
    }

    public static boolean active() {
        return CostRegistry.ticksActive;
    }

    /** Charges each registered placed feature, structure and carver to its namespace's mod. */
    public static void index(RegistryAccess access, ModOwners resolver) {
        Map<Object, String> namespaces = new IdentityHashMap<>();
        access.registries().forEach((RegistryAccess.RegistryEntry<?> entry) -> {
            if (REGISTRIES.contains(VanillaApi.keyPath(entry.key()))) {
                collect(entry.value(), namespaces);
            }
        });
        index(namespaces, resolver);
    }

    static void index(Map<Object, String> namespaces, ModOwners resolver) {
        Map<Object, CostBucket> buckets = new IdentityHashMap<>(namespaces.size() * 2);
        Set<String> seen = new TreeSet<>();
        for (Map.Entry<Object, String> entry : namespaces.entrySet()) {
            String owner = ownerOf(entry.getValue(), resolver);
            seen.add(owner);
            buckets.put(entry.getKey(), CostRegistry.bucket(owner, CostKind.WORLDGEN));
        }
        index = Collections.unmodifiableMap(buckets);
        owners = Collections.unmodifiableSet(seen);
    }

    static void clear() {
        index = Map.of();
        owners = Set.of();
    }

    /** Owners with registered world generation content, sorted. */
    public static Set<String> owners() {
        return owners;
    }

    /** A loaded mod's id for its namespace, else the namespace itself, as datapacks have no mod. */
    static String ownerOf(String namespace, ModOwners resolver) {
        String normalized = namespace == null || namespace.isBlank() ? ModOwners.UNKNOWN : namespace.toLowerCase(Locale.ROOT);
        String owner = resolver == null ? null : resolver.ownerOfNamespace(normalized);
        return owner == null ? normalized : owner;
    }

    public static CostBucket bucket(Object content) {
        CostBucket bucket = index.get(content);
        return bucket != null ? bucket : UNKNOWN;
    }

    public static long enter() {
        return CostFrame.enterAnyThread();
    }

    public static void exit(long start, CostBucket bucket) {
        CostFrame.exit(start, bucket);
    }

    public static void step(WorldgenStep step, long startNanos) {
        STEP_NANOS[step.ordinal()].add(System.nanoTime() - startNanos);
    }

    /** Adds each step's nanoseconds since the last drain to {@code into}, indexed by ordinal. */
    static void drainSteps(long[] into) {
        for (int step = 0; step < STEP_NANOS.length; step++) {
            into[step] += STEP_NANOS[step].sumThenReset();
        }
    }

    private static <T> void collect(Registry<T> registry, Map<Object, String> namespaces) {
        for (Map.Entry<ResourceKey<T>, T> entry : registry.entrySet()) {
            namespaces.put(entry.getValue(), VanillaApi.keyNamespace(entry.getKey()));
        }
    }
}
