package art.arcane.react.mod.cost;

import art.arcane.react.mod.VanillaApi;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Process-wide buckets and owner lookups. Tick hooks read {@link #ticksActive} and nothing else while idle. */
public final class CostRegistry {
    public static volatile boolean ticksActive;
    static volatile Thread serverThread;
    private static volatile ModOwners owners;
    private static final ConcurrentHashMap<String, CostBucket> BUCKETS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, String> CLASS_OWNERS = new ConcurrentHashMap<>();

    private CostRegistry() {
    }

    static void owners(ModOwners resolver) {
        owners = Objects.requireNonNull(resolver, "resolver");
    }

    public static List<ModOwners.LoadedMod> loadedMods() {
        ModOwners resolver = owners;
        return resolver == null ? List.of() : resolver.loadedMods();
    }

    public static Map<String, String> costOwners() {
        ModOwners resolver = owners;
        return resolver == null ? Map.of() : resolver.costOwners();
    }

    public static CostBucket bucket(String ownerId, CostKind kind) {
        return BUCKETS.computeIfAbsent(kind.name() + '/' + ownerId, (String key) -> new CostBucket(ownerId, kind));
    }

    public static String ownerOf(Class<?> type) {
        Class<?> declaring = ModOwners.declaringClass(type);
        String cached = CLASS_OWNERS.get(declaring);
        if (cached != null) {
            return cached;
        }
        ModOwners resolver = owners;
        String owner = resolver == null ? ModOwners.UNKNOWN : resolver.ownerOf(declaring);
        CLASS_OWNERS.put(declaring, owner);
        return owner;
    }

    public static CostBucket bucketFor(Class<?> type, CostKind kind) {
        return bucket(ownerOf(type), kind);
    }

    public static CostBucket blockEntityBucket(BlockEntity blockEntity) {
        CostBucketHolder holder = (CostBucketHolder) (Object) blockEntity.getType();
        CostBucket bucket = holder.react$costBucket();
        if (bucket == null) {
            bucket = bucket(owner(VanillaApi.namespace(blockEntity.getType()), blockEntity.getClass()), CostKind.BLOCK_ENTITY);
            holder.react$costBucket(bucket);
        }
        return bucket;
    }

    public static CostBucket entityBucket(Entity entity) {
        CostBucketHolder holder = (CostBucketHolder) (Object) entity.getType();
        CostBucket bucket = holder.react$costBucket();
        if (bucket == null) {
            bucket = bucket(owner(VanillaApi.namespace(entity.getType()), entity.getClass()), CostKind.ENTITY);
            holder.react$costBucket(bucket);
        }
        return bucket;
    }

    private static String owner(String namespace, Class<?> type) {
        ModOwners resolver = owners;
        String owner = namespace == null || resolver == null ? null : resolver.ownerOfNamespace(namespace);
        return owner != null ? owner : ownerOf(type);
    }

    static List<CostBucket> buckets() {
        return new ArrayList<>(BUCKETS.values());
    }
}
