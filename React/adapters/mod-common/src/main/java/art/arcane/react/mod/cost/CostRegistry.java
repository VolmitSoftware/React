package art.arcane.react.mod.cost;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
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
            Identifier key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
            bucket = bucket(owner(key, blockEntity.getClass()), CostKind.BLOCK_ENTITY);
            holder.react$costBucket(bucket);
        }
        return bucket;
    }

    public static CostBucket entityBucket(Entity entity) {
        CostBucketHolder holder = (CostBucketHolder) (Object) entity.getType();
        CostBucket bucket = holder.react$costBucket();
        if (bucket == null) {
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            bucket = bucket(owner(key, entity.getClass()), CostKind.ENTITY);
            holder.react$costBucket(bucket);
        }
        return bucket;
    }

    private static String owner(Identifier key, Class<?> type) {
        ModOwners resolver = owners;
        String owner = key == null || resolver == null ? null : resolver.ownerOfNamespace(key.getNamespace());
        return owner != null ? owner : ownerOf(type);
    }

    static List<CostBucket> buckets() {
        return new ArrayList<>(BUCKETS.values());
    }
}
