package art.arcane.react.mod;

import art.arcane.react.platform.EntityRange;
import art.arcane.react.platform.EntityRanges;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.raid.Raider;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spigot's per-world entity tracking and activation ranges, kept by React on mods, where the loaders have none.
 * Every level starts at Spigot's defaults, which leave vanilla untouched. A tracking range set below its default
 * scales the vanilla tracking range of that category's entities by the same ratio as they start being tracked. An
 * activation range set below its default turns on Spigot-style activation for the level: entities of that category
 * farther than the range from every player, horizontally, skip their tick unless an immunity keeps them awake.
 */
public final class ModEntityRanges implements EntityRanges {
    private static final Set<EntityRange> SUPPORTED =
            Collections.unmodifiableSet(EnumSet.complementOf(EnumSet.of(EntityRange.TRACKING_ITEM)));
    private static final int[] DEFAULTS = defaults();
    private static final int IMMUNITY_RECHECK_TICKS = 20;
    private static final Map<UUID, ModEntityRanges> LEVELS = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, EntityRange> TRACKING_CATEGORIES = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, EntityRange> ACTIVATION_CATEGORIES = new ConcurrentHashMap<>();
    private final int[] ranges = DEFAULTS.clone();
    private volatile boolean tickInactiveVillagers = true;
    private volatile boolean trackingGoverned;
    private volatile boolean activationGoverned;

    private ModEntityRanges() {
    }

    static ModEntityRanges of(ServerLevel level) {
        return LEVELS.computeIfAbsent(ModReactWorld.idOf(level), ignored -> new ModEntityRanges());
    }

    static void clear() {
        LEVELS.clear();
    }

    private static ModEntityRanges peek(ServerLevel level) {
        return LEVELS.isEmpty() ? null : LEVELS.get(ModReactWorld.idOf(level));
    }

    @Override
    public Set<EntityRange> supportedRanges() {
        return SUPPORTED;
    }

    @Override
    public int range(EntityRange type) {
        requireSupported(type);
        return ranges[type.ordinal()];
    }

    @Override
    public void range(EntityRange type, int blocks) {
        requireSupported(type);
        ranges[type.ordinal()] = blocks;
        trackingGoverned = anyBelowDefault(true);
        activationGoverned = anyBelowDefault(false);
    }

    @Override
    public boolean tickInactiveVillagers() {
        return tickInactiveVillagers;
    }

    @Override
    public void tickInactiveVillagers(boolean enabled) {
        tickInactiveVillagers = enabled;
    }

    /**
     * The tracking range, in blocks, an entity starts being tracked with.
     */
    public static int trackingRange(Entity entity, int vanillaRange) {
        if (vanillaRange <= 0 || !(entity.level() instanceof ServerLevel level)) {
            return vanillaRange;
        }
        ModEntityRanges state = peek(level);
        if (state == null || !state.trackingGoverned) {
            return vanillaRange;
        }
        EntityRange category = trackingCategory(entity);
        int base = DEFAULTS[category.ordinal()];
        int current = state.ranges[category.ordinal()];
        if (base <= 0 || current >= base) {
            return vanillaRange;
        }
        return Math.max(1, (int) Math.round(vanillaRange * (current / (double) base)));
    }

    /**
     * Runs Spigot's inactive tick in place of the entity's own when it is outside every governed activation range;
     * true when it did, so the caller skips the normal tick.
     */
    public static boolean tickInactive(ServerLevel level, Entity entity) {
        if (isActive(level, entity)) {
            return false;
        }
        VanillaCompat.inactiveTick(entity);
        return true;
    }

    private static boolean isActive(ServerLevel level, Entity entity) {
        ModEntityRanges state = peek(level);
        if (state == null || !state.activationGoverned || alwaysActive(entity)) {
            return true;
        }
        EntityRange category = activationCategory(entity);
        int range = state.ranges[category.ordinal()];
        if (range <= 0 || range >= DEFAULTS[category.ordinal()]) {
            return true;
        }
        if (category == EntityRange.ACTIVATION_VILLAGER && state.tickInactiveVillagers) {
            return true;
        }
        long now = level.getServer().getTickCount();
        ModActivationState activation = (ModActivationState) entity;
        if (activation.react$activatedTick() >= now || nearPlayer(level.players(), entity, range)) {
            activation.react$activatedTick(now);
            return true;
        }
        if ((now - activation.react$activatedTick() - 1) % IMMUNITY_RECHECK_TICKS == 0) {
            if (immune(entity)) {
                activation.react$activatedTick(now + IMMUNITY_RECHECK_TICKS);
            }
            return true;
        }
        return false;
    }

    static EntityRange trackingCategory(Entity entity) {
        return TRACKING_CATEGORIES.computeIfAbsent(entity.getType(), ignored -> classifyTracking(entity));
    }

    static EntityRange activationCategory(Entity entity) {
        return ACTIVATION_CATEGORIES.computeIfAbsent(entity.getType(), ignored -> classifyActivation(entity));
    }

    private static EntityRange classifyTracking(Entity entity) {
        if (entity instanceof ServerPlayer) {
            return EntityRange.TRACKING_PLAYER;
        }
        if (entity instanceof Display) {
            return EntityRange.TRACKING_DISPLAY;
        }
        if (entity instanceof Enemy) {
            return EntityRange.TRACKING_MONSTER;
        }
        if (entity instanceof PathfinderMob || entity instanceof AmbientCreature) {
            return EntityRange.TRACKING_ANIMAL;
        }
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof HangingEntity) {
            return EntityRange.TRACKING_MISC;
        }
        return EntityRange.TRACKING_OTHER;
    }

    private static EntityRange classifyActivation(Entity entity) {
        MobCategory category = entity.getType().getCategory();
        if (category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT
                || category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AXOLOTLS) {
            return EntityRange.ACTIVATION_WATER;
        }
        String type = ModGameEntity.typeName(entity.getType());
        if ("VILLAGER".equals(type)) {
            return EntityRange.ACTIVATION_VILLAGER;
        }
        if ("GHAST".equals(type) || "PHANTOM".equals(type)) {
            return EntityRange.ACTIVATION_FLYING_MONSTER;
        }
        if (entity instanceof Raider) {
            return EntityRange.ACTIVATION_RAIDER;
        }
        if (entity instanceof Enemy) {
            return EntityRange.ACTIVATION_MONSTER;
        }
        if (entity instanceof PathfinderMob || entity instanceof AmbientCreature) {
            return EntityRange.ACTIVATION_ANIMAL;
        }
        return EntityRange.ACTIVATION_MISC;
    }

    private static boolean alwaysActive(Entity entity) {
        return entity instanceof ServerPlayer
                || entity instanceof LightningBolt
                || entity instanceof PrimedTnt
                || entity instanceof FallingBlockEntity
                || entity instanceof EnderDragon
                || entity instanceof WitherBoss
                || entity instanceof EndCrystal
                || entity.isVehicle()
                || entity.isPassenger();
    }

    private static boolean immune(Entity entity) {
        if (entity.isInWater() || entity.getRemainingFireTicks() > 0) {
            return true;
        }
        if (entity instanceof Projectile) {
            if (entity.getDeltaMovement().lengthSqr() > 1.0E-7D) {
                return true;
            }
        } else if (!entity.onGround()) {
            return true;
        }
        if (entity instanceof LivingEntity living && (living.hurtTime > 0 || !living.getActiveEffects().isEmpty())) {
            return true;
        }
        if (entity instanceof Mob mob && mob.getTarget() != null) {
            return true;
        }
        if (entity instanceof Animal animal && (animal.isBaby() || animal.isInLove())) {
            return true;
        }
        return entity instanceof Creeper creeper && creeper.isIgnited();
    }

    private static boolean nearPlayer(List<ServerPlayer> players, Entity entity, int range) {
        double x = entity.getX();
        double z = entity.getZ();
        for (ServerPlayer player : players) {
            if (!player.isSpectator() && Math.abs(player.getX() - x) <= range && Math.abs(player.getZ() - z) <= range) {
                return true;
            }
        }
        return false;
    }

    private boolean anyBelowDefault(boolean tracking) {
        for (EntityRange type : SUPPORTED) {
            if (type.name().startsWith("TRACKING_") == tracking && ranges[type.ordinal()] < DEFAULTS[type.ordinal()]) {
                return true;
            }
        }
        return false;
    }

    private static void requireSupported(EntityRange type) {
        if (!SUPPORTED.contains(type)) {
            throw new UnsupportedOperationException("Separate item tracking ranges are unavailable");
        }
    }

    private static int[] defaults() {
        int[] values = new int[EntityRange.values().length];
        values[EntityRange.TRACKING_PLAYER.ordinal()] = 48;
        values[EntityRange.TRACKING_MISC.ordinal()] = 32;
        values[EntityRange.TRACKING_DISPLAY.ordinal()] = 128;
        values[EntityRange.TRACKING_ANIMAL.ordinal()] = 48;
        values[EntityRange.TRACKING_MONSTER.ordinal()] = 48;
        values[EntityRange.TRACKING_OTHER.ordinal()] = 64;
        values[EntityRange.ACTIVATION_ANIMAL.ordinal()] = 32;
        values[EntityRange.ACTIVATION_MONSTER.ordinal()] = 32;
        values[EntityRange.ACTIVATION_RAIDER.ordinal()] = 64;
        values[EntityRange.ACTIVATION_MISC.ordinal()] = 16;
        values[EntityRange.ACTIVATION_WATER.ordinal()] = 16;
        values[EntityRange.ACTIVATION_VILLAGER.ordinal()] = 32;
        values[EntityRange.ACTIVATION_FLYING_MONSTER.ordinal()] = 32;
        return values;
    }
}
