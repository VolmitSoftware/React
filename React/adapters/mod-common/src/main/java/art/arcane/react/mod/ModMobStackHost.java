package art.arcane.react.mod;

import art.arcane.react.mod.mixin.ChunkMapAccess;
import art.arcane.react.mod.mixin.CreeperAccess;
import art.arcane.react.mod.mixin.EndermanAccess;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.MobStackHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mob stacking on mods. Type-specific state is compared and copied through the entity's saved data, so the keys
 * below follow vanilla's save format. Flags live only in memory, as Bukkit metadata does; the stack label is an
 * entity tag so it survives restarts.
 */
public final class ModMobStackHost implements MobStackHost {
    private static final List<Events> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Map<Entity, Set<String>> FLAGS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Class<?>, Field> GOAL_OWNERS = new ConcurrentHashMap<>();
    private static final String LABEL_TAG = "react.mob-stack-label:";
    private static final int COLLECT_RADIUS = 64;
    private static final List<String> ANIMAL_KEYS = List.of("Age", "AgeLocked", "InLove", "LoveCause");
    private static final List<String> CREEPER_KEYS = List.of("powered", "Fuse", "ExplosionRadius");
    private static final List<String> FARM_VARIANT_KEYS = List.of("variant", "sound_variant");
    private static final Set<String> IDENTITY_KEYS = Set.of("UUID", "Pos", "Tags", "Passengers", "leash", "Leash",
            "Brain", "CustomName");
    private static final List<EquipmentSlot> BUKKIT_EQUIPMENT = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);
    private static final Set<String> VARIANT_BEARING = Set.of("AXOLOTL", "BEE", "BOGGED", "CAMEL", "CAT",
            "DONKEY", "ENDER_DRAGON", "FOX", "FROG", "GHAST", "GOAT", "HOGLIN",
            "HORSE", "IRON_GOLEM", "LLAMA", "MOOSHROOM", "MULE",
            "MUSHROOM_COW", "OCELOT", "PANDA", "PARROT", "PHANTOM", "PIGLIN",
            "PIGLIN_BRUTE", "RABBIT", "SALMON", "SKELETON_HORSE",
            "SNIFFER", "SNOW_GOLEM", "STRIDER", "TRADER_LLAMA", "TROPICAL_FISH", "VEX",
            "VILLAGER", "WANDERING_TRADER", "WOLF", "ZOMBIE_HORSE",
            "ZOMBIE_NAUTILUS", "ZOMBIE_VILLAGER", "ZOMBIFIED_PIGLIN");

    private final MinecraftServer server;
    private final AtomicReference<List<ChunkRef>> loadedChunks = new AtomicReference<>();
    private final Map<Entity, CompoundTag> snapshots = new IdentityHashMap<>();
    private long snapshotTick = -1L;
    private volatile boolean snapshotQueued;

    ModMobStackHost(MinecraftServer server) {
        this.server = server;
    }

    static void clear() {
        LISTENERS.clear();
        FLAGS.clear();
    }

    public static void died(LivingEntity entity) {
        if (LISTENERS.isEmpty() || !(entity.level() instanceof ServerLevel)) {
            return;
        }
        GameEntity wrapped = new ModGameEntity(entity);
        for (Events events : LISTENERS) {
            events.onDeath(wrapped);
        }
    }

    public static void tamed(Entity entity, Player owner) {
        if (LISTENERS.isEmpty() || owner == null || !(entity.level() instanceof ServerLevel)) {
            return;
        }
        GameEntity wrapped = new ModGameEntity(entity);
        UUID ownerId = owner.getUUID();
        for (Events events : LISTENERS) {
            events.onTame(wrapped, ownerId);
        }
    }

    public static void interacted(Player player, Entity entity, InteractionHand hand) {
        if (LISTENERS.isEmpty() || !(player instanceof ServerPlayer) || entity == null
                || !player.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND) {
            return;
        }
        GameEntity wrapped = new ModGameEntity(entity);
        for (Events events : LISTENERS) {
            events.onSneakInteract(wrapped);
        }
    }

    /** False when a listener vetoed the creeper charge or enderman block change. */
    public static boolean allowStateChange(Entity entity) {
        if (LISTENERS.isEmpty() || entity == null || !(entity.level() instanceof ServerLevel)) {
            return true;
        }
        GameEntity wrapped = new ModGameEntity(entity);
        for (Events events : LISTENERS) {
            if (!events.beforeStateChange(wrapped)) {
                return false;
            }
        }
        return true;
    }

    /** The enderman a block goal belongs to, read from the goal's {@code enderman} field. */
    public static Entity goalOwner(Object goal) {
        Field field = GOAL_OWNERS.computeIfAbsent(goal.getClass(), ModMobStackHost::ownerField);
        try {
            return field == null ? null : (Entity) field.get(goal);
        } catch (IllegalAccessException failure) {
            return null;
        }
    }

    private static Field ownerField(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (Entity.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    public static void exploded(Creeper creeper) {
        removed(creeper, true);
    }

    public static void removed(Entity entity, boolean exploded) {
        if (LISTENERS.isEmpty()) {
            return;
        }
        GameEntity wrapped = new ModGameEntity(entity);
        for (Events events : LISTENERS) {
            events.onRemove(wrapped, exploded);
        }
    }

    public static void loaded(Entity entity) {
        if (LISTENERS.isEmpty()) {
            return;
        }
        List<GameEntity> wrapped = List.of(new ModGameEntity(entity));
        for (Events events : LISTENERS) {
            events.onEntitiesLoad(wrapped);
        }
    }

    @Override
    public Subscription listen(Events events) {
        LISTENERS.add(events);
        ReactPlatform platform = ReactPlatforms.getOrNull();
        Subscription spawns = MobStackHost.spawnEvents(platform == null ? null : platform.spawnHost().orElse(null), events);
        return () -> {
            spawns.cancel();
            LISTENERS.remove(events);
        };
    }

    @Override
    public Set<String> defaultStackableTypes() {
        Set<String> types = new HashSet<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if ("minecraft".equals(VanillaApi.namespace(type)) && type.canSummon() && DefaultAttributes.hasSupplier(type)) {
                types.add(ModGameEntity.typeName(type));
            }
        }
        types.remove("PLAYER");
        types.remove("ARMOR_STAND");
        types.remove("VILLAGER");
        types.remove("WANDERING_TRADER");
        types.remove("FALLING_BLOCK");
        return types;
    }


    @Override
    public boolean isLiving(GameEntity entity) {
        return handle(entity) instanceof LivingEntity;
    }

    @Override
    public boolean isDead(GameEntity entity) {
        return !handle(entity).isAlive();
    }

    @Override
    public boolean isAlive(GameEntity entity) {
        Entity handle = handle(entity);
        return handle.isAlive() && !handle.isRemoved() && handle.level() instanceof ServerLevel;
    }

    @Override
    public boolean isSameEntity(GameEntity first, GameEntity second) {
        return handle(first).getId() == handle(second).getId();
    }

    @Override
    public boolean isTamedPet(GameEntity entity) {
        return VanillaApi.isTamed(handle(entity));
    }

    @Override
    public UUID ownerId(GameEntity entity) {
        return VanillaApi.ownerId(handle(entity));
    }

    @Override
    public Object mergeBucket(GameEntity entity) {
        Entity handle = handle(entity);
        boolean baby = handle instanceof LivingEntity living && living.isBaby();
        return new StackBucket(handle.getType(), Math.round(handle.getBbWidth() * 1000F), !baby);
    }

    @Override
    public boolean hasSafeMergeState(GameEntity source, GameEntity target) {
        LivingEntity left = living(source);
        LivingEntity right = living(target);
        if (!hasMatchingHostileState(source.type(), left, right)) {
            return false;
        }
        if (!hasMatchingFarmState(source.type(), left, right)) {
            return false;
        }
        if (hasEquipment(left) || hasEquipment(right)) {
            return false;
        }
        return left.getActiveEffects().isEmpty() && right.getActiveEffects().isEmpty();
    }

    @Override
    public boolean hasSafeHealthState(GameEntity source, GameEntity target) {
        if (VARIANT_BEARING.contains(source.type()) || VARIANT_BEARING.contains(target.type())) {
            return false;
        }
        LivingEntity left = living(source);
        LivingEntity right = living(target);
        AttributeInstance sourceMaxHealth = left.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance targetMaxHealth = right.getAttribute(Attributes.MAX_HEALTH);
        if (sourceMaxHealth == null || targetMaxHealth == null) {
            return false;
        }
        if (!sourceMaxHealth.getModifiers().isEmpty() || !targetMaxHealth.getModifiers().isEmpty()) {
            return false;
        }
        if (Math.abs(sourceMaxHealth.getValue() - targetMaxHealth.getValue()) > 0.0001D) {
            return false;
        }
        return Math.abs(left.getHealth() - sourceMaxHealth.getValue()) <= 0.0001D
                && Math.abs(right.getHealth() - targetMaxHealth.getValue()) <= 0.0001D;
    }

    @Override
    public double maxHealth(GameEntity entity) {
        AttributeInstance attribute = living(entity).getAttribute(Attributes.MAX_HEALTH);
        return attribute == null ? Double.NaN : attribute.getValue();
    }

    @Override
    public double health(GameEntity entity) {
        return living(entity).getHealth();
    }

    @Override
    public void setHealth(GameEntity entity, double health) {
        living(entity).setHealth((float) health);
    }

    @Override
    public void restoreHealth(GameEntity entity, double health) {
        LivingEntity living = living(entity);
        living.setHealth((float) Math.min(living.getMaxHealth(), health));
    }

    @Override
    public GameEntity spawnCopy(GameEntity source, double raise, boolean copyName) {
        LivingEntity living = living(source);
        if (!(living.level() instanceof ServerLevel level)) {
            return null;
        }
        Entity created = VanillaApi.createEntity(living.getType(), level);
        if (!(created instanceof LivingEntity copy)) {
            return null;
        }
        VanillaApi.place(copy, living.getX(), living.getY() + raise, living.getZ(), living.getYRot(), living.getXRot());
        copyState(living, copy, copyName);
        if (!level.addFreshEntity(copy) || copy.isRemoved()) {
            copy.discard();
            return null;
        }
        return new ModGameEntity(copy);
    }

    private void copyState(LivingEntity source, LivingEntity target, boolean copyName) {
        CompoundTag state = VanillaApi.saveEntity(source).copy();
        CompoundTag own = VanillaApi.saveEntity(target);
        for (String key : IDENTITY_KEYS) {
            state.remove(key);
            Tag kept = own.get(key);
            if (kept != null && !"CustomName".equals(key) && !"Tags".equals(key)) {
                state.put(key, kept);
            }
        }
        state.remove("ignited");
        VanillaApi.loadEntity(target, state);
        copyProvocation(source, target);
        Component name = source.getCustomName();
        target.setCustomName(copyName && name != null ? name.copy() : null);
        float maxHealth = source.getMaxHealth();
        if (Float.isFinite(maxHealth) && maxHealth > 0F) {
            AttributeInstance attribute = target.getAttribute(Attributes.MAX_HEALTH);
            if (attribute != null) {
                attribute.setBaseValue(maxHealth);
            }
            target.setHealth(maxHealth);
        }
        Set<String> flags = FLAGS.get(source);
        if (flags != null) {
            for (String flag : List.of(SPAWNED_BY_SPAWNER, DO_NOT_STACK, UNIQUE_STACK)) {
                if (flags.contains(flag)) {
                    flag(target, flag, true);
                }
            }
        }
    }

    @Override
    public void untame(GameEntity entity) {
        Entity handle = handle(entity);
        if (!VanillaApi.isTamed(handle) && VanillaApi.ownerId(handle) == null) {
            return;
        }
        CompoundTag state = VanillaApi.saveEntity(handle);
        state.remove("Owner");
        state.putBoolean("Tame", false);
        VanillaApi.loadEntity(handle, state);
        if (VanillaApi.isTamed(handle)) {
            throw new IllegalStateException("The replacement " + entity.type() + " kept its owner.");
        }
    }

    @Override
    public void discard(GameEntity entity) {
        handle(entity).discard();
    }

    @Override
    public void sendCollect(GameEntity collected, GameEntity collector) {
        Entity source = handle(collected);
        if (!(source.level() instanceof ServerLevel level)) {
            return;
        }
        ClientboundTakeItemEntityPacket packet = new ClientboundTakeItemEntityPacket(source.getId(), handle(collector).getId(), 1);
        double radius = COLLECT_RADIUS * COLLECT_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(source) <= radius) {
                player.connection.send(packet);
            }
        }
    }

    @Override
    public boolean hasFlag(GameEntity entity, String flag) {
        Set<String> flags = FLAGS.get(handle(entity));
        return flags != null && flags.contains(flag);
    }

    @Override
    public void setFlag(GameEntity entity, String flag, boolean value) {
        flag(handle(entity), flag, value);
    }

    private static void flag(Entity entity, String flag, boolean value) {
        synchronized (FLAGS) {
            Set<String> flags = FLAGS.get(entity);
            if (value) {
                if (flags == null) {
                    flags = ConcurrentHashMap.newKeySet();
                    FLAGS.put(entity, flags);
                }
                flags.add(flag);
            } else if (flags != null) {
                flags.remove(flag);
                if (flags.isEmpty()) {
                    FLAGS.remove(entity);
                }
            }
        }
    }

    @Override
    public String customName(GameEntity entity) {
        Component name = handle(entity).getCustomName();
        return name == null ? null : legacy(name);
    }

    @Override
    public void setCustomName(GameEntity entity, String name) {
        handle(entity).setCustomName(name == null ? null : ModLegacyText.component(name));
    }

    @Override
    public String stackLabel(GameEntity entity) {
        for (String tag : VanillaApi.tags(handle(entity))) {
            if (tag.startsWith(LABEL_TAG)) {
                return tag.substring(LABEL_TAG.length());
            }
        }
        return null;
    }

    @Override
    public void setStackLabel(GameEntity entity, String label) {
        clearStackLabel(entity);
        handle(entity).addTag(LABEL_TAG + label);
    }

    @Override
    public void clearStackLabel(GameEntity entity) {
        Entity handle = handle(entity);
        for (String tag : new ArrayList<>(VanillaApi.tags(handle))) {
            if (tag.startsWith(LABEL_TAG)) {
                handle.removeTag(tag);
            }
        }
    }

    @Override
    public boolean showOverlay(GameEntity entity, int count) {
        return false;
    }

    @Override
    public boolean isChunkLoaded(ReactWorld world, int chunkX, int chunkZ) {
        return ((ServerLevel) world.handle()).getChunkSource().hasChunk(chunkX, chunkZ);
    }

    @Override
    public List<GameEntity> loadedChunkEntities(ReactWorld world, int chunkX, int chunkZ) {
        ServerLevel level = (ServerLevel) world.handle();
        if (!level.getChunkSource().hasChunk(chunkX, chunkZ) || !level.areEntitiesLoaded(VanillaApi.pack(chunkX, chunkZ))) {
            return null;
        }
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        AABB box = new AABB(minX, VanillaApi.minY(level), minZ, minX + 16, VanillaApi.maxY(level) + 1, minZ + 16);
        List<Entity> entities = level.getEntities((Entity) null, box,
                (Entity entity) -> entity.getBlockX() >> 4 == chunkX && entity.getBlockZ() >> 4 == chunkZ);
        return new AbstractList<>() {
            @Override
            public GameEntity get(int index) {
                return new ModGameEntity(entities.get(index));
            }

            @Override
            public int size() {
                return entities.size();
            }
        };
    }

    @Override
    public ChunkCursor openLoadedChunks() {
        List<ChunkRef> snapshot = loadedChunks.getAndSet(null);
        if (snapshot == null) {
            if (!snapshotQueued) {
                snapshotQueued = true;
                server.execute(this::snapshotLoadedChunks);
            }
            return null;
        }
        int[] position = new int[1];
        return maximum -> {
            int from = position[0];
            int to = Math.min(snapshot.size(), from + Math.max(0, maximum));
            position[0] = to;
            return new ArrayList<>(snapshot.subList(from, to));
        };
    }

    private void snapshotLoadedChunks() {
        try {
            List<ChunkRef> chunks = new ArrayList<>();
            for (ServerLevel level : server.getAllLevels()) {
                UUID worldId = ModReactWorld.idOf(level);
                for (long key : ((ChunkMapAccess) level.getChunkSource().chunkMap).react$visibleChunkMap().keySet()) {
                    chunks.add(new ChunkRef(worldId, (int) key, (int) (key >>> 32)));
                }
            }
            loadedChunks.set(chunks);
        } finally {
            snapshotQueued = false;
        }
    }

    private boolean hasMatchingHostileState(String type, LivingEntity source, LivingEntity target) {
        boolean creeper = "CREEPER".equals(type);
        boolean enderman = "ENDERMAN".equals(type);
        if (!creeper && !enderman) {
            return true;
        }
        if (source.isPassenger() || target.isPassenger()
                || !source.getPassengers().isEmpty() || !target.getPassengers().isEmpty()) {
            return false;
        }
        if (creeper && source instanceof Creeper left && target instanceof Creeper right) {
            return sameKeys(left, right, CREEPER_KEYS)
                    && !left.isIgnited() && !right.isIgnited()
                    && ((CreeperAccess) left).react$swell() == 0 && ((CreeperAccess) right).react$swell() == 0;
        }
        return source instanceof Mob left && target instanceof Mob right
                && left.getTarget() == null && right.getTarget() == null
                && sameKeys(left, right, List.of("carriedBlockState"))
                && !isProvoked(left) && !isProvoked(right);
    }

    private static boolean isProvoked(Entity entity) {
        return entity instanceof EndermanAccess enderman && (enderman.react$isCreepy() || enderman.react$hasBeenStaredAt());
    }

    private static void copyProvocation(Entity source, Entity target) {
        if (source instanceof EndermanAccess left && target instanceof EndermanAccess right) {
            target.getEntityData().set(EndermanAccess.react$creepyData(), left.react$isCreepy());
            if (left.react$hasBeenStaredAt() && !right.react$hasBeenStaredAt()) {
                right.react$setBeingStaredAt();
            }
        }
    }

    private boolean hasMatchingFarmState(String type, LivingEntity source, LivingEntity target) {
        if (!(source instanceof Animal left) || !(target instanceof Animal right)) {
            return true;
        }
        if (left.isLeashed() || right.isLeashed()
                || left.isPassenger() || right.isPassenger()
                || !left.getPassengers().isEmpty() || !right.getPassengers().isEmpty()
                || !sameKeys(left, right, ANIMAL_KEYS)) {
            return false;
        }
        switch (type) {
            case "COW":
                return sameKeys(left, right, FARM_VARIANT_KEYS);
            case "CHICKEN":
                return sameKeys(left, right, FARM_VARIANT_KEYS) && sameKeys(left, right, List.of("IsChickenJockey"));
            case "PIG":
                return sameKeys(left, right, FARM_VARIANT_KEYS) && sameKeys(left, right, List.of("Saddle", "equipment"));
            case "SHEEP":
                return sameKeys(left, right, List.of("Sheared", "Color"));
            default:
                return true;
        }
    }

    private boolean sameKeys(Entity left, Entity right, List<String> keys) {
        CompoundTag first = snapshot(left);
        CompoundTag second = snapshot(right);
        for (String key : keys) {
            if (!Objects.equals(first.get(key), second.get(key))) {
                return false;
            }
        }
        return true;
    }

    private synchronized CompoundTag snapshot(Entity entity) {
        long tick = server.getTickCount();
        if (tick != snapshotTick) {
            snapshots.clear();
            snapshotTick = tick;
        }
        CompoundTag state = snapshots.get(entity);
        if (state == null) {
            state = VanillaApi.saveEntity(entity);
            snapshots.put(entity, state);
        }
        return state;
    }

    private static boolean hasEquipment(LivingEntity entity) {
        for (EquipmentSlot slot : BUKKIT_EQUIPMENT) {
            if (!entity.getItemBySlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static String legacy(Component component) {
        StringBuilder result = new StringBuilder();
        component.visit((Style style, String text) -> {
            if (text.isEmpty()) {
                return Optional.empty();
            }
            TextColor color = style.getColor();
            ChatFormatting named = color == null ? null : named(color.serialize());
            if (named != null) {
                result.append(named);
            } else if (color != null) {
                String hex = String.format("%06x", color.getValue() & 0xFFFFFF);
                result.append("§x");
                for (int index = 0; index < hex.length(); index++) {
                    result.append('§').append(hex.charAt(index));
                }
            } else if (result.length() > 0) {
                result.append("§r");
            }
            if (style.isObfuscated()) {
                result.append("§k");
            }
            if (style.isBold()) {
                result.append("§l");
            }
            if (style.isStrikethrough()) {
                result.append("§m");
            }
            if (style.isUnderlined()) {
                result.append("§n");
            }
            if (style.isItalic()) {
                result.append("§o");
            }
            result.append(text);
            return Optional.empty();
        }, Style.EMPTY);
        return result.toString();
    }

    private static ChatFormatting named(String color) {
        for (ChatFormatting formatting : ChatFormatting.values()) {
            if (formatting.name().toLowerCase(Locale.ROOT).equals(color)) {
                return formatting;
            }
        }
        return null;
    }

    private static Entity handle(GameEntity entity) {
        return (Entity) entity.handle();
    }

    private static LivingEntity living(GameEntity entity) {
        return (LivingEntity) entity.handle();
    }

    private record StackBucket(EntityType<?> type, int width, boolean adult) {
    }
}
