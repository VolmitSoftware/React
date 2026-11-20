package art.arcane.react.mod;

import art.arcane.react.platform.GameLocation;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.net.URI;

/** Vanilla calls whose names or shapes changed after 1.21.1; the mapped source family carries the older forms. */
public final class VanillaApi {
    private VanillaApi() {
    }

    static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    static DyedItemColor dyedColor(int rgb) {
        return new DyedItemColor(rgb);
    }

    static Item vanillaItem(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(path));
    }

    public static String namespace(BlockEntityType<?> type) {
        Identifier key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
        return key == null ? null : key.getNamespace();
    }

    public static String namespace(EntityType<?> type) {
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key == null ? null : key.getNamespace();
    }

    static Item itemOrAir(String key) {
        Identifier id = Identifier.tryParse(key);
        return id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
    }

    public static String path(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
    }

    static ParticleOptions whiteFlash() {
        return ColorParticleOption.create(ParticleTypes.FLASH, 1F, 1F, 1F);
    }

    static String dimensionId(ResourceKey<Level> dimension) {
        return dimension.identifier().toString();
    }

    static String keyPath(ResourceKey<?> key) {
        return key.identifier().getPath();
    }

    static String minecraftVersion() {
        return SharedConstants.getCurrentVersion().name();
    }

    static boolean allowFlight(MinecraftServer server) {
        return server.allowFlight();
    }

    static double averageTickMillis(MinecraftServer server) {
        return server.getAverageTickTimeNanos() / 1_000_000D;
    }

    static int idleTimeoutMinutes(MinecraftServer server) {
        return server.playerIdleTimeout();
    }

    static int chunkX(ChunkPos pos) {
        return pos.x();
    }

    static int chunkZ(ChunkPos pos) {
        return pos.z();
    }

    static long pack(ChunkPos pos) {
        return pos.pack();
    }

    static long pack(int chunkX, int chunkZ) {
        return ChunkPos.pack(chunkX, chunkZ);
    }

    static java.util.Set<String> tags(net.minecraft.world.entity.Entity entity) {
        return entity.entityTags();
    }

    static ServerLevel level(ServerPlayer player) {
        return player.level();
    }

    static BlockPos spawn(ServerLevel level) {
        return level.getRespawnData().pos();
    }

    static int minY(Level level) {
        return level.getMinY();
    }

    static int maxY(Level level) {
        return level.getMaxY();
    }

    static int randomTickSpeed(ServerLevel level) {
        return level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED);
    }

    static void setRandomTickSpeed(ServerLevel level, int value) {
        level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED, value, level.getServer());
    }

    static ServerPlayer killer(net.minecraft.world.entity.LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
        return victim.getLastHurtByPlayer() instanceof ServerPlayer ? (ServerPlayer) victim.getLastHurtByPlayer() : null;
    }

    static boolean isSurvival(ServerPlayer player) {
        return player.gameMode() == net.minecraft.world.level.GameType.SURVIVAL;
    }

    static int forcedChunkCount(ServerLevel level) {
        return level.getForceLoadedChunks().size();
    }

    static ItemStack selectedItem(Inventory inventory) {
        return inventory.getSelectedItem();
    }

    static int selectedSlot(Inventory inventory) {
        return inventory.getSelectedSlot();
    }

    static net.minecraft.network.chat.Style font(net.minecraft.network.chat.Style style, String namespace, String path) {
        return style.withFont(new net.minecraft.network.chat.FontDescription.Resource(Identifier.fromNamespaceAndPath(namespace, path)));
    }

    static void selectItem(Inventory inventory, ItemStack item) {
        inventory.setSelectedItem(item);
    }

    static CompoundTag compound(CompoundTag tag, String key) {
        return tag.getCompoundOrEmpty(key);
    }

    static byte byteOr(CompoundTag tag, String key, byte fallback) {
        return tag.getByteOr(key, fallback);
    }

    static String stringOr(CompoundTag tag, String key, String fallback) {
        return tag.getStringOr(key, fallback);
    }

    static ClickEvent click(net.kyori.adventure.text.event.ClickEvent.Action action, String value) {
        return switch (action) {
            case RUN_COMMAND -> new ClickEvent.RunCommand(value);
            case SUGGEST_COMMAND -> new ClickEvent.SuggestCommand(value);
            case COPY_TO_CLIPBOARD -> new ClickEvent.CopyToClipboard(value);
            case OPEN_URL -> new ClickEvent.OpenUrl(URI.create(value));
            default -> null;
        };
    }

    static HoverEvent showText(Component text) {
        return new HoverEvent.ShowText(text);
    }

    static boolean granted(CommandSourceStack source, String atom) {
        return granted(source.permissions(), atom);
    }

    static boolean granted(ServerPlayer player, String atom) {
        return granted(player.permissions(), atom);
    }

    static boolean gamemaster(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    static boolean gamemaster(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static boolean granted(PermissionSet permissions, String atom) {
        Identifier key = atom == null ? null : Identifier.tryParse(atom);
        return key != null && permissions.hasPermission(Permission.Atom.create(key));
    }

    /** Where a portal transition leads, or null for a transition React cannot read. */
    static GameLocation portalDestination(Object transition) {
        if (!(transition instanceof TeleportTransition teleport) || teleport.newLevel() == null) {
            return null;
        }
        Vec3 position = teleport.position();
        return new GameLocation(ModReactWorld.idOf(teleport.newLevel()), position.x, position.y, position.z);
    }

    /** Sends the entity along a portal transition captured earlier; false when the trip failed. */
    static boolean travel(Entity entity, Object transition) {
        return transition instanceof TeleportTransition teleport && entity.teleport(teleport) != null;
    }
}
