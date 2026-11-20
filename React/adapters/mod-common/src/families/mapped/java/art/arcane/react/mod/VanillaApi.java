package art.arcane.react.mod;

import art.arcane.react.platform.GameLocation;
import net.minecraft.SharedConstants;
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
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/** The 1.21.1 forms of the vanilla calls whose names or shapes changed later. */
public final class VanillaApi {
    private static final int GAMEMASTER_LEVEL = 2;

    private VanillaApi() {
    }

    static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    static Item vanillaItem(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(path));
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

    /** Where a portal transition leads, or null for a transition React cannot read. */
    static GameLocation portalDestination(Object transition) {
        if (!(transition instanceof DimensionTransition dimension) || dimension.newLevel() == null) {
            return null;
        }
        Vec3 position = dimension.pos();
        return new GameLocation(ModReactWorld.idOf(dimension.newLevel()), position.x, position.y, position.z);
    }

    /** Sends the entity along a portal transition captured earlier; false when the trip failed. */
    static boolean travel(Entity entity, Object transition) {
        return transition instanceof DimensionTransition dimension && entity.changeDimension(dimension) != null;
    }
}
