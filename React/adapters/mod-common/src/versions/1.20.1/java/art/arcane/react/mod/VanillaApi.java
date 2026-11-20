package art.arcane.react.mod;

import art.arcane.react.platform.GameLocation;
import net.minecraft.SharedConstants;
import java.util.Locale;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;

/** The 1.20.1 forms of the vanilla calls whose names or shapes changed later. */
public final class VanillaApi {
    private static final int GAMEMASTER_LEVEL = 2;

    private VanillaApi() {
    }

    static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    static Item vanillaItem(String path) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(path));
    }

    public static String namespace(BlockEntityType<?> type) {
        ResourceLocation key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
        return key == null ? null : key.getNamespace();
    }

    public static String namespace(EntityType<?> type) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key == null ? null : key.getNamespace();
    }

    static Item itemOrAir(String key) {
        ResourceLocation id = ResourceLocation.tryParse(key);
        return id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
    }

    public static String path(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
    }

    static ParticleOptions whiteFlash() {
        return ParticleTypes.FLASH;
    }

    static String dimensionId(ResourceKey<Level> dimension) {
        return dimension.location().toString();
    }

    static String keyPath(ResourceKey<?> key) {
        return key.location().getPath();
    }

    static String minecraftVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    static int chunkX(ChunkPos pos) {
        return pos.x;
    }

    static int chunkZ(ChunkPos pos) {
        return pos.z;
    }

    static long pack(ChunkPos pos) {
        return pos.toLong();
    }

    static long pack(int chunkX, int chunkZ) {
        return ChunkPos.asLong(chunkX, chunkZ);
    }

    static ServerLevel level(ServerPlayer player) {
        return player.serverLevel();
    }

    static BlockPos spawn(ServerLevel level) {
        return level.getSharedSpawnPos();
    }

    static int minY(Level level) {
        return level.getMinBuildHeight();
    }

    static int maxY(Level level) {
        return level.getMaxBuildHeight() - 1;
    }

    static int randomTickSpeed(ServerLevel level) {
        return level.getGameRules().getInt(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING);
    }

    static void setRandomTickSpeed(ServerLevel level, int value) {
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING).set(value, level.getServer());
    }

    static ServerPlayer killer(net.minecraft.world.entity.LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
        net.minecraft.world.entity.Entity attacker = source == null ? null : source.getEntity();
        return attacker instanceof ServerPlayer ? (ServerPlayer) attacker : null;
    }

    static boolean isSurvival(ServerPlayer player) {
        return player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SURVIVAL;
    }

    static int forcedChunkCount(ServerLevel level) {
        return level.getForcedChunks().size();
    }

    static boolean allowFlight(MinecraftServer server) {
        return server.isFlightAllowed();
    }

    static double averageTickMillis(MinecraftServer server) {
        return server.getAverageTickTime();
    }

    static int idleTimeoutMinutes(MinecraftServer server) {
        return server.getPlayerIdleTimeout();
    }

    static java.util.Set<String> tags(net.minecraft.world.entity.Entity entity) {
        return entity.getTags();
    }

    static int selectedSlot(Inventory inventory) {
        return inventory.selected;
    }

    static net.minecraft.network.chat.Style font(net.minecraft.network.chat.Style style, String namespace, String path) {
        return style.withFont(new ResourceLocation(namespace, path));
    }

    static ItemStack selectedItem(Inventory inventory) {
        return inventory.getSelected();
    }

    static void selectItem(Inventory inventory, ItemStack item) {
        inventory.items.set(inventory.selected, item);
    }

    static CompoundTag compound(CompoundTag tag, String key) {
        return tag.getCompound(key);
    }

    static byte byteOr(CompoundTag tag, String key, byte fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getByte(key) : fallback;
    }

    static String stringOr(CompoundTag tag, String key, String fallback) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
    }

    static ClickEvent click(net.kyori.adventure.text.event.ClickEvent.Action action, String value) {
        ClickEvent.Action vanilla = switch (action) {
            case RUN_COMMAND -> ClickEvent.Action.RUN_COMMAND;
            case SUGGEST_COMMAND -> ClickEvent.Action.SUGGEST_COMMAND;
            case COPY_TO_CLIPBOARD -> ClickEvent.Action.COPY_TO_CLIPBOARD;
            case OPEN_URL -> ClickEvent.Action.OPEN_URL;
            default -> null;
        };
        return vanilla == null ? null : new ClickEvent(vanilla, value);
    }

    static HoverEvent showText(Component text) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
    }

    static boolean granted(CommandSourceStack source, String atom) {
        return false;
    }

    static boolean granted(ServerPlayer player, String atom) {
        return false;
    }

    static boolean gamemaster(CommandSourceStack source) {
        return source.hasPermission(GAMEMASTER_LEVEL);
    }

    static boolean gamemaster(ServerPlayer player) {
        return player.hasPermissions(GAMEMASTER_LEVEL);
    }

    /** Where a portal trip leads, or null for a trip React cannot read. */
    static GameLocation portalDestination(Object transition) {
        if (!(transition instanceof PortalTrip trip)) {
            return null;
        }
        Vec3 position = trip.info().pos;
        return new GameLocation(ModReactWorld.idOf(trip.level()), position.x, position.y, position.z);
    }

    /** Sends the entity along a portal trip captured earlier; false when the trip failed. */
    static boolean travel(Entity entity, Object transition) {
        return transition instanceof PortalTrip trip && trip.entity() == entity && trip.resume();
    }

    /** How far the player reaches blocks. */
    static double blockReach(ServerPlayer player) {
        return player.isCreative() ? 5.0D : 4.5D;
    }

    static Packet<?> mergedExplosionPacket(double x, double y, double z, float radius) {
        return new ClientboundExplodePacket(x, y, z, radius, List.of(), null);
    }

    /** A block's type as React's config files name it: the Bukkit material constant for vanilla blocks. */
    public static String blockTypeName(Block block) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        return "minecraft".equals(key.getNamespace()) ? key.getPath().toUpperCase(Locale.ROOT) : key.toString();
    }

    static SoundEvent sound(String key) {
        ResourceLocation id = key == null ? null : ResourceLocation.tryParse(key);
        return id == null ? null : SoundEvent.createVariableRangeEvent(id);
    }

    /**
     * The block or item named the way {@link #blockTypeName} names it; air when there is none.
     */
    static Block blockByName(String name) {
        ResourceLocation id = configId(name);
        return id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(id);
    }

    static Item itemByName(String name) {
        ResourceLocation id = configId(name);
        return id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
    }

    private static ResourceLocation configId(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return name.indexOf(':') >= 0 ? ResourceLocation.tryParse(name) : ResourceLocation.tryParse("minecraft:" + name.toLowerCase(Locale.ROOT));
    }

    static ParticleOptions itemParticle(Item item) {
        return new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(item));
    }

    static void tickFluid(FluidState fluid, ServerLevel level, BlockPos pos, BlockState block) {
        fluid.tick(level, pos);
    }

    /** True when both stacks hold the same item with the same data, so they can merge. */
    static boolean sameItemSameData(ItemStack first, ItemStack second) {
        return ItemStack.isSameItemSameTags(first, second);
    }
}
