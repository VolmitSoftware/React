package art.arcane.react.mod;

import art.arcane.react.platform.DropHost;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.Subscription;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fast drops on a mod server. While an entity drops its death loot, or a player breaks a block, the item entities and
 * experience that vanilla spawns are held back and handed to the listeners as one death or block drop, the way Bukkit's
 * death, block drop and block break events carry them. Whatever the listeners leave is spawned as vanilla would have.
 */
public final class ModDropHost implements DropHost {
    private static final List<DropHost.Listener<EntityDeath>> DEATHS = new CopyOnWriteArrayList<>();
    private static final List<DropHost.Listener<BlockDrops>> BLOCK_DROPS = new CopyOnWriteArrayList<>();
    private static final List<DropHost.Listener<BlockBreak>> BREAKS = new CopyOnWriteArrayList<>();
    private static final Deque<Capture> CAPTURES = new ArrayDeque<>();
    private static boolean releasing;

    private static final class Capture {
        private final ServerLevel level;
        private final LivingEntity victim;
        private final ServerPlayer killer;
        private final ServerPlayer breaker;
        private final boolean container;
        private final List<ItemEntity> items = new ArrayList<>();
        private final List<Vec3> experienceAt = new ArrayList<>();
        private int experience;

        private Capture(ServerLevel level, LivingEntity victim, ServerPlayer killer, ServerPlayer breaker, boolean container) {
            this.level = level;
            this.victim = victim;
            this.killer = killer;
            this.breaker = breaker;
            this.container = container;
        }
    }

    static void clear() {
        DEATHS.clear();
        BLOCK_DROPS.clear();
        BREAKS.clear();
        CAPTURES.clear();
    }

    /** Server thread, at the start of {@code LivingEntity.dropAllDeathLoot}. */
    public static void beginDeath(LivingEntity victim, DamageSource source) {
        if (DEATHS.isEmpty() || !(victim.level() instanceof ServerLevel)) {
            return;
        }
        CAPTURES.push(new Capture((ServerLevel) victim.level(), victim, VanillaApi.killer(victim, source), null, false));
    }

    /** Server thread, when {@code LivingEntity.dropAllDeathLoot} returns. */
    public static void endDeath(LivingEntity victim) {
        Capture capture = CAPTURES.peek();
        if (capture == null || capture.victim != victim) {
            return;
        }
        CAPTURES.pop();
        ModEntityDeath death = new ModEntityDeath(capture);
        for (DropHost.Listener<EntityDeath> listener : DEATHS) {
            listener.on(death);
        }
        release(capture);
    }

    /** Server thread, at the start of {@code ServerPlayerGameMode.destroyBlock}. */
    public static void beginBreak(ServerPlayer player, BlockPos pos) {
        if (BLOCK_DROPS.isEmpty() && BREAKS.isEmpty()) {
            return;
        }
        ServerLevel level = VanillaApi.level(player);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        CAPTURES.push(new Capture(level, null, null, player, blockEntity instanceof Container));
    }

    /** Server thread, when {@code ServerPlayerGameMode.destroyBlock} returns. */
    public static void endBreak(ServerPlayer player) {
        Capture capture = CAPTURES.peek();
        if (capture == null || capture.breaker != player) {
            return;
        }
        CAPTURES.pop();
        ModBlockBreak broken = new ModBlockBreak(capture);
        for (DropHost.Listener<BlockBreak> listener : BREAKS) {
            listener.on(broken);
        }
        ModBlockDrops drops = new ModBlockDrops(capture);
        for (DropHost.Listener<BlockDrops> listener : BLOCK_DROPS) {
            listener.on(drops);
        }
        release(capture);
    }

    /** True when the item entity was held back for the drop being captured. */
    public static boolean captureItem(ServerLevel level, Entity entity) {
        Capture capture = CAPTURES.peek();
        if (releasing || capture == null || capture.level != level || !(entity instanceof ItemEntity)) {
            return false;
        }
        capture.items.add((ItemEntity) entity);
        return true;
    }

    /** True when the experience was held back for the drop being captured. */
    public static boolean captureExperience(ServerLevel level, Vec3 at, int amount) {
        Capture capture = CAPTURES.peek();
        if (releasing || capture == null || capture.level != level || amount <= 0) {
            return false;
        }
        capture.experience += amount;
        capture.experienceAt.add(at);
        return true;
    }

    private static void release(Capture capture) {
        boolean previous = releasing;
        releasing = true;
        try {
            for (ItemEntity item : capture.items) {
                if (!item.getItem().isEmpty()) {
                    capture.level.addFreshEntity(item);
                }
            }
            if (capture.experience > 0 && !capture.experienceAt.isEmpty()) {
                ExperienceOrb.award(capture.level, capture.experienceAt.get(0), capture.experience);
            }
        } finally {
            releasing = previous;
        }
    }

    @Override
    public Subscription onEntityDeath(DropHost.Listener<EntityDeath> listener) {
        DEATHS.add(listener);
        return () -> DEATHS.remove(listener);
    }

    @Override
    public Subscription onBlockDrops(DropHost.Listener<BlockDrops> listener) {
        BLOCK_DROPS.add(listener);
        return () -> BLOCK_DROPS.remove(listener);
    }

    @Override
    public Subscription onBlockBreak(DropHost.Listener<BlockBreak> listener) {
        BREAKS.add(listener);
        return () -> BREAKS.remove(listener);
    }

    @Override
    public Object copyStack(Object stack) {
        return ((ItemStack) stack).copy();
    }

    @Override
    public boolean isCreative(ReactPlayer player) {
        return player(player).isCreative();
    }

    @Override
    public boolean isSurvival(ReactPlayer player) {
        return VanillaApi.isSurvival(player(player));
    }

    @Override
    public void awardExperience(ReactPlayer player, int experience) {
        if (experience > 0) {
            player(player).giveExperiencePoints(experience);
        }
    }

    @Override
    public boolean give(ReactPlayer player, Object stack, Object dropAt) {
        ServerPlayer server = player(player);
        ItemStack item = (ItemStack) stack;
        server.getInventory().add(item);
        if (item.isEmpty()) {
            return false;
        }
        dropNaturally(player, dropAt, item);
        return true;
    }

    @Override
    public void dropNaturally(ReactPlayer player, Object dropAt, Object stack) {
        ServerPlayer server = player(player);
        Vec3 at = dropAt instanceof Vec3 ? (Vec3) dropAt : server.position();
        boolean previous = releasing;
        releasing = true;
        try {
            Containers.dropItemStack(VanillaApi.level(server), at.x, at.y, at.z, (ItemStack) stack);
        } finally {
            releasing = previous;
        }
    }

    @Override
    public void playSound(ReactPlayer player, String soundKey, float volume, float pitch) {
        SoundEvent sound = "minecraft:entity.experience_orb.pickup".equals(soundKey) ? SoundEvents.EXPERIENCE_ORB_PICKUP
                : "minecraft:entity.item.pickup".equals(soundKey) ? SoundEvents.ITEM_PICKUP : null;
        if (sound == null) {
            return;
        }
        ServerPlayer server = player(player);
        server.connection.send(new ClientboundSoundPacket(Holder.direct(sound), SoundSource.PLAYERS, server.getX(), server.getY(),
                server.getZ(), volume, pitch, ThreadLocalRandom.current().nextLong()));
    }

    private static ServerPlayer player(ReactPlayer player) {
        return (ServerPlayer) player.handle();
    }

    private static final class ModEntityDeath implements EntityDeath {
        private final Capture capture;

        private ModEntityDeath(Capture capture) {
            this.capture = capture;
        }

        @Override
        public ReactPlayer killer() {
            return capture.killer == null ? null : new ModReactPlayer(capture.killer);
        }

        @Override
        public double killerDistanceSquared() {
            if (capture.killer == null || capture.killer.level() != capture.victim.level()) {
                return Double.POSITIVE_INFINITY;
            }
            return capture.killer.distanceToSqr(capture.victim);
        }

        @Override
        public int droppedExperience() {
            return capture.experience;
        }

        @Override
        public void setDroppedExperience(int experience) {
            capture.experience = Math.max(0, experience);
        }

        @Override
        public List<Object> takeDrops() {
            List<Object> drops = new ArrayList<>();
            for (ItemEntity item : capture.items) {
                drops.add(item.getItem());
            }
            capture.items.clear();
            return drops;
        }
    }

    private static final class ModBlockDrops implements BlockDrops {
        private final Capture capture;

        private ModBlockDrops(Capture capture) {
            this.capture = capture;
        }

        @Override
        public ReactPlayer player() {
            return new ModReactPlayer(capture.breaker);
        }

        @Override
        public boolean fromContainer() {
            return capture.container;
        }

        @Override
        public List<DroppedItem> items() {
            List<DroppedItem> items = new ArrayList<>();
            for (ItemEntity item : capture.items) {
                items.add(new ModDroppedItem(capture, item));
            }
            return items;
        }
    }

    private static final class ModDroppedItem implements DroppedItem {
        private final Capture capture;
        private final ItemEntity item;

        private ModDroppedItem(Capture capture, ItemEntity item) {
            this.capture = capture;
            this.item = item;
        }

        @Override
        public Object copyStack() {
            return item.getItem().copy();
        }

        @Override
        public boolean remove() {
            return capture.items.remove(item);
        }

        @Override
        public Object dropLocation() {
            return item.position();
        }
    }

    private static final class ModBlockBreak implements BlockBreak {
        private final Capture capture;

        private ModBlockBreak(Capture capture) {
            this.capture = capture;
        }

        @Override
        public ReactPlayer player() {
            return new ModReactPlayer(capture.breaker);
        }

        @Override
        public int experience() {
            return capture.experience;
        }

        @Override
        public void setExperience(int experience) {
            capture.experience = Math.max(0, experience);
        }
    }
}
