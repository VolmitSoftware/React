package art.arcane.react.mod;

import art.arcane.react.platform.GameLocation;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

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

    /** True for the player who owns the singleplayer world this integrated server runs. */
    static boolean worldOwner(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return ModPermissions.worldOwner(server.isDedicatedServer(), server.isSingleplayerOwner(player.nameAndId()));
    }

    private static boolean granted(PermissionSet permissions, String atom) {
        Identifier key = atom == null ? null : Identifier.tryParse(atom);
        return key != null && permissions.hasPermission(Permission.Atom.create(key));
    }

    // Batch A: mobs
    static CompoundTag saveEntity(Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    static void loadEntity(Entity entity, CompoundTag tag) {
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, entity.registryAccess(), tag));
    }

    static Entity createEntity(EntityType<?> type, ServerLevel level) {
        return type.create(level, EntitySpawnReason.MOB_SUMMONED);
    }

    static void place(Entity entity, double x, double y, double z, float yRot, float xRot) {
        entity.snapTo(x, y, z, yRot, xRot);
    }

    static UUID ownerId(Entity entity) {
        if (!(entity instanceof OwnableEntity ownable)) {
            return null;
        }
        EntityReference<LivingEntity> owner = ownable.getOwnerReference();
        return owner == null ? null : owner.getUUID();
    }

    static boolean isTameable(Entity entity) {
        return entity instanceof TamableAnimal || entity instanceof AbstractHorse;
    }

    static boolean isTamed(Entity entity) {
        if (entity instanceof TamableAnimal tamable) {
            return tamable.isTame();
        }
        return entity instanceof AbstractHorse horse && horse.isTamed();
    }

    static List<ModItemValue.Recipe> recipes(MinecraftServer server) {
        List<ModItemValue.Recipe> recipes = new ArrayList<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            try {
                ModItemValue.Recipe recipe = recipe(holder.value());
                if (recipe != null) {
                    recipes.add(recipe);
                }
            } catch (RuntimeException ignored) {
            }
        }
        return recipes;
    }

    private static ModItemValue.Recipe recipe(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            return ModItemValue.shaped(shaped.assemble(CraftingInput.EMPTY), firstItems(shaped.placementInfo().ingredients()));
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return ModItemValue.shapeless(shapeless.assemble(CraftingInput.EMPTY), firstItems(shapeless.placementInfo().ingredients()));
        }
        if (recipe instanceof AbstractCookingRecipe || recipe instanceof StonecutterRecipe) {
            SingleItemRecipe single = (SingleItemRecipe) recipe;
            List<Item> input = new ArrayList<>(1);
            input.add(firstItem(single.input()));
            return ModItemValue.shapeless(single.assemble(new SingleRecipeInput(ItemStack.EMPTY)), input);
        }
        return null;
    }

    private static List<Item> firstItems(List<Ingredient> ingredients) {
        List<Item> items = new ArrayList<>(ingredients.size());
        for (Ingredient ingredient : ingredients) {
            items.add(firstItem(ingredient));
        }
        return items;
    }

    private static Item firstItem(Ingredient ingredient) {
        Optional<Holder<Item>> first = ingredient.items().findFirst();
        return first.isPresent() ? first.get().value() : null;
    }

    // Batch A: items
    static String itemNamespace(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getNamespace();
    }

    static String itemPath(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    static List<ItemStack> bundleItems(BundleContents contents) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStackTemplate template : contents.items()) {
            items.add(template.create());
        }
        return items;
    }

    static BundleContents bundleContents(List<ItemStack> items) {
        List<ItemStackTemplate> templates = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                templates.add(ItemStackTemplate.fromNonEmptyStack(item));
            }
        }
        return new BundleContents(templates);
    }

    static ParticleOptions itemParticle(ItemStack item) {
        return new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(item));
    }

    static boolean isMinecart(Entity entity) {
        return entity instanceof AbstractMinecart;
    }

    static boolean isBoat(Entity entity) {
        return entity instanceof AbstractBoat;
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

    /** How far the player reaches blocks. */
    static double blockReach(ServerPlayer player) {
        return player.blockInteractionRange();
    }

    static Packet<?> mergedExplosionPacket(double x, double y, double z, float radius) {
        return VanillaCompat.mergedExplosionPacket(x, y, z, radius);
    }

    /** A block's type as React's config files name it: the Bukkit material constant for vanilla blocks. */
    public static String blockTypeName(Block block) {
        Identifier key = BuiltInRegistries.BLOCK.getKey(block);
        return "minecraft".equals(key.getNamespace()) ? key.getPath().toUpperCase(Locale.ROOT) : key.toString();
    }

    static SoundEvent sound(String key) {
        Identifier id = key == null ? null : Identifier.tryParse(key);
        return id == null ? null : SoundEvent.createVariableRangeEvent(id);
    }

    /**
     * The block or item named the way {@link #blockTypeName} names it; air when there is none.
     */
    static Block blockByName(String name) {
        Identifier id = configId(name);
        return id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.getValue(id);
    }

    static Item itemByName(String name) {
        Identifier id = configId(name);
        return id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
    }

    private static Identifier configId(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return name.indexOf(':') >= 0 ? Identifier.tryParse(name) : Identifier.tryParse("minecraft:" + name.toLowerCase(Locale.ROOT));
    }

    static ParticleOptions itemParticle(Item item) {
        return new ItemParticleOption(ParticleTypes.ITEM, item);
    }

    static void tickFluid(FluidState fluid, ServerLevel level, BlockPos pos, BlockState block) {
        fluid.tick(level, pos, block);
    }

    /** True when both stacks hold the same item with the same data, so they can merge. */
    static boolean sameItemSameData(ItemStack first, ItemStack second) {
        return ItemStack.isSameItemSameComponents(first, second);
    }
}
