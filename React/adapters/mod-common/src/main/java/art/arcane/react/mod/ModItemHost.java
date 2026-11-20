package art.arcane.react.mod;

import art.arcane.react.mod.mixin.ExperienceOrbAccess;
import art.arcane.react.mod.mixin.ItemEntityAccess;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ItemHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.SpawnHost;
import art.arcane.react.platform.Subscription;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Item entities, super stacks and experience orbs on mods. Spawns and loads come from the server level and entity
 * callback mixins, pickups from the item entity, hopper and container menu mixins. Super stacks use Bukkit's format:
 * a bundle holding the merged stacks with a single "REACT SUPER STACK" lore line, so they move between both sides.
 */
public final class ModItemHost implements ItemHost {
    private static final String SUPER_STACK_LORE = "REACT SUPER STACK";
    private static final int SINGLE_TYPE_LIMIT = 64;
    private static final double SOUND_RANGE_SQUARED = 32D * 32D;
    private static final int SOUND_RECIPIENTS = 64;
    private static final List<Consumer<GameEntity>> LOAD = new CopyOnWriteArrayList<>();
    private static final List<Consumer<GameEntity>> REMOVE = new CopyOnWriteArrayList<>();
    private static final List<Consumer<PlayerPickup>> PLAYER_PICKUP = new CopyOnWriteArrayList<>();
    private static final List<Consumer<ContainerPickup>> CONTAINER_PICKUP = new CopyOnWriteArrayList<>();
    private static final List<Consumer<SlotClick>> SLOT_CLICK = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Damage>> DAMAGE = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<Entity> ADDING = new ThreadLocal<>();
    private final Map<UUID, Integer> cursors = new ConcurrentHashMap<>();

    private static <T> Subscription subscribe(List<T> listeners, T listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    static void clear() {
        LOAD.clear();
        REMOVE.clear();
        PLAYER_PICKUP.clear();
        CONTAINER_PICKUP.clear();
        SLOT_CLICK.clear();
        DAMAGE.clear();
    }

    @Override
    public Subscription onSpawn(Consumer<GameEntity> listener) {
        return ItemHost.spawnsOf(spawns(), true, listener);
    }

    @Override
    public Subscription onLoad(Consumer<GameEntity> listener) {
        return subscribe(LOAD, listener);
    }

    @Override
    public Subscription onRemove(Consumer<GameEntity> listener) {
        return subscribe(REMOVE, listener);
    }

    @Override
    public Subscription onPlayerPickup(Consumer<PlayerPickup> listener) {
        return subscribe(PLAYER_PICKUP, listener);
    }

    @Override
    public Subscription onContainerPickup(Consumer<ContainerPickup> listener) {
        return subscribe(CONTAINER_PICKUP, listener);
    }

    @Override
    public Subscription onSlotClick(Consumer<SlotClick> listener) {
        return subscribe(SLOT_CLICK, listener);
    }

    @Override
    public Subscription onDamage(Consumer<Damage> listener) {
        return subscribe(DAMAGE, listener);
    }

    @Override
    public Subscription onOrbSpawn(Consumer<GameEntity> listener) {
        return ItemHost.spawnsOf(spawns(), false, listener);
    }

    public static void adding(Entity entity) {
        if (entity instanceof ItemEntity) {
            ADDING.set(entity);
        }
    }

    public static void added(Entity entity) {
        if (ADDING.get() == entity) {
            ADDING.remove();
        }
    }

    private static SpawnHost spawns() {
        ReactPlatform platform = ReactPlatforms.getOrNull();
        return platform == null ? null : platform.spawnHost().orElse(null);
    }

    public static void trackingStarted(Entity entity) {
        if (entity instanceof ItemEntity && ADDING.get() != entity && !LOAD.isEmpty() && entity.level() instanceof ServerLevel) {
            notify(LOAD, new ModGameEntity(entity));
        }
    }

    public static void trackingEnded(Entity entity) {
        if (entity instanceof ItemEntity && !REMOVE.isEmpty() && entity.level() instanceof ServerLevel) {
            notify(REMOVE, new ModGameEntity(entity));
        }
    }

    /** False when a listener cancelled the pickup of {@code item} by {@code player}. */
    public static boolean allowPlayerPickup(ItemEntity item, Player player) {
        if (PLAYER_PICKUP.isEmpty() || !(player instanceof ServerPlayer serverPlayer) || item.hasPickUpDelay()) {
            return true;
        }
        UUID target = ((ItemEntityAccess) item).react$target();
        if (target != null && !target.equals(player.getUUID())) {
            return true;
        }
        ItemStack stack = item.getItem();
        Inventory inventory = player.getInventory();
        if (stack.isEmpty() || (inventory.getFreeSlot() < 0 && inventory.getSlotWithRemainingSpace(stack) < 0)) {
            return true;
        }
        return dispatch(PLAYER_PICKUP, new ModPlayerPickup(item, serverPlayer));
    }

    /** False when a listener cancelled {@code container} pulling in {@code item}. */
    public static boolean allowContainerPickup(Container container, ItemEntity item) {
        if (CONTAINER_PICKUP.isEmpty() || !(item.level() instanceof ServerLevel)) {
            return true;
        }
        return dispatch(CONTAINER_PICKUP, new ModContainerPickup(item, container));
    }

    /** False when a listener unpacked the clicked super stack, which also cancels the click. */
    public static boolean allowSlotClick(AbstractContainerMenu menu, int slotIndex, Player player) {
        if (SLOT_CLICK.isEmpty() || !(player instanceof ServerPlayer serverPlayer) || slotIndex < 0 || slotIndex >= menu.slots.size()) {
            return true;
        }
        ModSlotClick click = new ModSlotClick(menu.getSlot(slotIndex), serverPlayer);
        for (Consumer<SlotClick> listener : SLOT_CLICK) {
            if (click.cancelled) {
                return false;
            }
            listener.accept(click);
        }
        return !click.cancelled;
    }

    /** False when a listener cancelled the damage to {@code item}. */
    public static boolean allowDamage(ItemEntity item, DamageSource source) {
        if (DAMAGE.isEmpty() || !(item.level() instanceof ServerLevel)) {
            return true;
        }
        return dispatch(DAMAGE, new ModDamage(item, source.is(DamageTypes.FELL_OUT_OF_WORLD)));
    }

    private static void notify(List<Consumer<GameEntity>> listeners, GameEntity entity) {
        for (Consumer<GameEntity> listener : listeners) {
            listener.accept(entity);
        }
    }

    private static <T extends ItemEvent> boolean dispatch(List<Consumer<T>> listeners, T event) {
        for (Consumer<T> listener : listeners) {
            if (event.isCancelled()) {
                return false;
            }
            listener.accept(event);
        }
        return !event.isCancelled();
    }

    @Override
    public GameEntity entity(Object handle) {
        return new ModGameEntity((Entity) handle);
    }

    @Override
    public boolean isItem(GameEntity entity) {
        return handle(entity) instanceof ItemEntity;
    }

    @Override
    public boolean isLive(GameEntity entity) {
        Entity handle = handle(entity);
        return handle != null && handle.isAlive() && !handle.isRemoved();
    }

    @Override
    public List<GameEntity> scan(ReactWorld world, int budget) {
        if (world == null || budget <= 0 || !(world.handle() instanceof ServerLevel level)) {
            return List.of();
        }
        List<Entity> snapshot = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity != null) {
                snapshot.add(entity);
            }
        }
        if (snapshot.isEmpty()) {
            return List.of();
        }
        UUID worldId = ModReactWorld.idOf(level);
        int size = snapshot.size();
        int start = Math.floorMod(cursors.getOrDefault(worldId, 0), size);
        int count = Math.min(budget, size);
        List<GameEntity> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new ModGameEntity(snapshot.get((start + index) % size)));
        }
        cursors.put(worldId, (start + count) % size);
        return result;
    }

    @Override
    public void discard(GameEntity entity) {
        Entity handle = handle(entity);
        if (handle != null) {
            handle.discard();
        }
    }

    @Override
    public String customName(GameEntity entity) {
        Entity handle = handle(entity);
        Component name = handle == null ? null : handle.getCustomName();
        return name == null ? null : name.getString();
    }

    @Override
    public String material(GameEntity item) {
        Item type = stack(item).getItem();
        String namespace = VanillaApi.itemNamespace(type);
        String path = VanillaApi.itemPath(type);
        return "minecraft".equals(namespace) ? path.toUpperCase(Locale.ROOT) : namespace + ":" + path;
    }

    @Override
    public int amount(GameEntity item) {
        return stack(item).getCount();
    }

    @Override
    public int maxStackSize(GameEntity item) {
        return stack(item).getMaxStackSize();
    }

    @Override
    public boolean isSimilar(GameEntity item, GameEntity other) {
        return VanillaItems.sameItem(stack(item), stack(other));
    }

    @Override
    public void setAmount(GameEntity item, int amount) {
        if (handle(item) instanceof ItemEntity entity) {
            entity.setItem(entity.getItem().copyWithCount(amount));
        }
    }

    @Override
    public void setTicksLived(GameEntity item, int ticks) {
        Entity handle = handle(item);
        if (handle == null) {
            return;
        }
        handle.tickCount = ticks;
        if (handle instanceof ItemEntity entity) {
            ((ItemEntityAccess) entity).react$setAge(ticks);
        }
    }

    @Override
    public boolean isSuperStack(GameEntity item) {
        return isFlagged(stack(item));
    }

    @Override
    public int superStackCount(GameEntity item) {
        return superStackCount(stack(item));
    }

    @Override
    public boolean bundle(GameEntity source, GameEntity target, int maxBundle) {
        if (!(handle(source) instanceof ItemEntity sourceItem) || !(handle(target) instanceof ItemEntity targetItem)) {
            return false;
        }
        List<ItemStack> items = new ArrayList<>(explode(sourceItem.getItem()));
        items.addAll(explode(targetItem.getItem()));
        int total = totalCount(items);
        Set<Item> types = new HashSet<>();
        for (ItemStack item : items) {
            types.add(item.getItem());
        }
        if (total > maxBundle || (types.size() <= 1 && total <= SINGLE_TYPE_LIMIT)) {
            return false;
        }
        ItemStack bundle = createBundle(items);
        if (bundle == null) {
            return false;
        }
        targetItem.setItem(bundle);
        return true;
    }

    @Override
    public void discardMerged(GameEntity item) {
        discard(item);
    }

    @Override
    public void mergeEffect(GameEntity item, GameEntity into, double radius, boolean sound) {
        if (!(handle(item) instanceof ItemEntity source) || !(source.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack stack = source.getItem();
        if (!stack.isEmpty()) {
            ParticleOptions particle = VanillaApi.itemParticle(stack);
            level.sendParticles(particle, source.getX(), source.getY(), source.getZ(), 7, 0.1D, 0.1D, 0.1D, 0.1D);
            Entity target = handle(into);
            if (target != null && target.level() == level) {
                double dx = target.getX() - source.getX();
                double dy = target.getY() - source.getY();
                double dz = target.getZ() - source.getZ();
                if (Math.sqrt(dx * dx + dy * dy + dz * dz) > 1.0E-6D) {
                    int steps = Math.max(1, (int) Math.ceil(radius * 2.0D));
                    for (int index = 1; index <= steps; index++) {
                        double progress = (double) index / steps;
                        level.sendParticles(particle, source.getX() + dx * progress, source.getY() + dy * progress,
                                source.getZ() + dz * progress, 3, 0D, 0D, 0D, 0D);
                    }
                }
            }
        }
        if (sound) {
            playMergeSound(level, source.getX(), source.getY(), source.getZ());
        }
    }

    private static void playMergeSound(ServerLevel level, double x, double y, double z) {
        RandomSource random = level.getRandom();
        float pitch = 1.2F + (random.nextFloat() * 0.2F - 0.1F);
        int sent = 0;
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (sent >= SOUND_RECIPIENTS) {
                return;
            }
            if (player.distanceToSqr(x, y, z) <= SOUND_RANGE_SQUARED) {
                playSound(player, SoundEvents.BUNDLE_INSERT, SoundSource.NEUTRAL, x, y, z, 0.5F, pitch);
                sent++;
            }
        }
    }

    private static void playSound(ServerPlayer player, SoundEvent sound, SoundSource source, double x, double y, double z,
                                  float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), source, x, y, z,
                volume, pitch, player.getRandom().nextLong()));
    }

    @Override
    public boolean refreshLabel(GameEntity item, String headerFormat, String entryFormat, String moreFormat, int entryLimit) {
        return false;
    }

    @Override
    public void clearLabel(GameEntity item) {
    }

    @Override
    public int experience(GameEntity orb) {
        return handle(orb) instanceof ExperienceOrb experienceOrb ? experienceOrb.getValue() : 0;
    }

    @Override
    public void setExperience(GameEntity orb, int experience) {
        if (handle(orb) instanceof ExperienceOrb experienceOrb) {
            ((ExperienceOrbAccess) experienceOrb).react$setValue(experience);
        }
    }

    @Override
    public List<GameEntity> nearbyOrbs(GameEntity orb, double radius) {
        if (!(handle(orb) instanceof ExperienceOrb experienceOrb)) {
            return List.of();
        }
        List<GameEntity> result = new ArrayList<>();
        for (ExperienceOrb other : experienceOrb.level().getEntitiesOfClass(ExperienceOrb.class,
                experienceOrb.getBoundingBox().inflate(radius), (ExperienceOrb candidate) -> candidate != experienceOrb)) {
            result.add(new ModGameEntity(other));
        }
        return result;
    }

    private static Entity handle(GameEntity entity) {
        return entity != null && entity.handle() instanceof Entity handle ? handle : null;
    }

    private static ItemStack stack(GameEntity item) {
        return handle(item) instanceof ItemEntity entity ? entity.getItem() : ItemStack.EMPTY;
    }

    static boolean isFlagged(ItemStack item) {
        if (item == null || !item.is(Items.BUNDLE)) {
            return false;
        }
        List<Component> lore = VanillaItems.lore(item);
        return lore.size() == 1 && SUPER_STACK_LORE.equals(lore.get(0).getString());
    }

    static int superStackCount(ItemStack item) {
        if (!isFlagged(item)) {
            return -1;
        }
        return totalCount(VanillaItems.bundleItems(item));
    }

    static List<ItemStack> explode(ItemStack item) {
        if (!item.is(Items.BUNDLE)) {
            List<ItemStack> single = new ArrayList<>();
            single.add(item.copy());
            return single;
        }
        return VanillaItems.bundleItems(item);
    }

    private static List<ItemStack> superStackContents(ItemStack item) {
        if (isFlagged(item)) {
            return explode(item);
        }
        List<ItemStack> single = new ArrayList<>();
        single.add(item.copy());
        return single;
    }

    static int totalCount(List<ItemStack> items) {
        int total = 0;
        for (ItemStack item : items) {
            total += item.getCount();
        }
        return total;
    }

    static List<ItemStack> compact(List<ItemStack> items) {
        List<ItemStack> compacted = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            ItemStack remaining = item.copy();
            for (ItemStack existing : compacted) {
                if (!VanillaItems.sameItem(existing, remaining) || existing.getCount() >= existing.getMaxStackSize()) {
                    continue;
                }
                int transferred = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(transferred);
                remaining.shrink(transferred);
                if (remaining.isEmpty()) {
                    break;
                }
            }
            if (!remaining.isEmpty()) {
                compacted.add(remaining);
            }
        }
        return compacted;
    }

    static ItemStack createBundle(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        List<ItemStack> compacted = compact(items);
        if (compacted.isEmpty()) {
            return null;
        }
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        VanillaItems.bundleItems(bundle, compacted);
        VanillaItems.lore(bundle, List.of(Component.literal(SUPER_STACK_LORE)));
        return bundle;
    }

    private static void giveTo(ServerPlayer player, List<ItemStack> items) {
        for (ItemStack item : items) {
            ItemStack remaining = item.copy();
            player.getInventory().add(remaining);
            if (!remaining.isEmpty()) {
                drop(VanillaApi.level(player), player.getX(), player.getY(), player.getZ(), remaining);
            }
        }
    }

    private static void drop(Level level, double x, double y, double z, ItemStack item) {
        ItemEntity dropped = new ItemEntity(level, x, y, z, item);
        dropped.setDefaultPickUpDelay();
        level.addFreshEntity(dropped);
    }

    private abstract static class ModItemEvent implements ItemEvent {
        final ItemEntity item;
        boolean cancelled;

        ModItemEvent(ItemEntity item) {
            this.item = item;
        }

        @Override
        public GameEntity item() {
            return new ModGameEntity(item);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private static final class ModPlayerPickup extends ModItemEvent implements PlayerPickup {
        private final ServerPlayer player;

        private ModPlayerPickup(ItemEntity item, ServerPlayer player) {
            super(item);
            this.player = player;
        }

        @Override
        public void giveContents() {
            giveTo(player, superStackContents(item.getItem()));
        }

        @Override
        public void playUnpackSound() {
            float pitch = 0.85F + (player.getRandom().nextFloat() * 0.2F - 0.1F);
            playSound(player, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, item.getX(), item.getY(), item.getZ(), 1F, pitch);
        }
    }

    private static final class ModContainerPickup extends ModItemEvent implements ContainerPickup {
        private final Container container;
        private int kept;

        private ModContainerPickup(ItemEntity item, Container container) {
            super(item);
            this.container = container;
        }

        @Override
        public Insertion insertContents() {
            List<ItemStack> leftovers = new ArrayList<>();
            for (ItemStack content : superStackContents(item.getItem())) {
                ItemStack leftover = HopperBlockEntity.addItem(null, container, content.copy(), null);
                if (!leftover.isEmpty()) {
                    leftovers.add(leftover);
                }
            }
            if (leftovers.isEmpty()) {
                kept = 0;
                return Insertion.INSERTED;
            }
            ItemStack residual = createBundle(leftovers);
            if (residual != null) {
                item.setItem(residual);
                kept = totalCount(leftovers);
                return Insertion.KEPT;
            }
            kept = 0;
            for (ItemStack leftover : leftovers) {
                drop(item.level(), item.getX(), item.getY(), item.getZ(), leftover);
            }
            return Insertion.DROPPED;
        }

        @Override
        public int keptCount() {
            return kept;
        }
    }

    private static final class ModSlotClick implements SlotClick {
        private final Slot slot;
        private final ServerPlayer player;
        private boolean cancelled;

        private ModSlotClick(Slot slot, ServerPlayer player) {
            this.slot = slot;
            this.player = player;
        }

        @Override
        public boolean isSuperStack() {
            return isFlagged(slot.getItem());
        }

        @Override
        public void unpack() {
            ItemStack item = slot.getItem();
            if (!isFlagged(item)) {
                return;
            }
            cancelled = true;
            List<ItemStack> contents = explode(item);
            slot.set(ItemStack.EMPTY);
            giveTo(player, contents);
        }
    }

    private static final class ModDamage extends ModItemEvent implements Damage {
        private final boolean isVoid;

        private ModDamage(ItemEntity item, boolean isVoid) {
            super(item);
            this.isVoid = isVoid;
        }

        @Override
        public boolean isVoid() {
            return isVoid;
        }
    }
}
