package art.arcane.react.mod;

import art.arcane.react.content.tweak.TweakShorthands;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.ShorthandMessages;
import art.arcane.react.platform.ShorthandHost;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.volmlib.util.localization.MessageArgument;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The shorthand commands as brigadier literals on a mod server, with the same switches, permissions, messages and
 * label protection as Bukkit's command map registration. Each command is also registered as {@code react:<label>}.
 */
final class ModShorthandHost implements ShorthandHost {
    private static final String COMMAND_NAMESPACE = "react";
    private static final String ARGUMENTS = "arguments";
    private static final String NO_PERMISSION = "I'm sorry, but you do not have permission to perform this command. "
            + "Please contact the server administrators if you believe that this is in error.";
    private static final int MAX_GIVE_STACKS = 100;
    private static final Pattern COMMAND_LABEL = Pattern.compile("[a-z0-9][a-z0-9_-]*");
    private final MinecraftServer server;
    private final ThreadLocal<Set<String>> activeCustomAliases = ThreadLocal.withInitial(HashSet::new);

    ModShorthandHost(MinecraftServer server) {
        this.server = server;
    }

    @FunctionalInterface
    private interface Handler {
        void run(CommandSourceStack source, String[] args);
    }

    @FunctionalInterface
    private interface Completer {
        CompletableFuture<Suggestions> complete(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder);
    }

    private record Registration(String label, CommandNode<CommandSourceStack> node, CommandNode<CommandSourceStack> namespaced,
                                CommandNode<CommandSourceStack> previousDirect, CommandNode<CommandSourceStack> previousNamespaced) {
    }

    @Override
    public boolean runOnCommandThread(Runnable operation) {
        if (server.isSameThread()) {
            operation.run();
            return true;
        }
        server.execute(operation);
        return true;
    }

    @Override
    public Subscription register(TweakShorthands settings) {
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        Map<String, Registration> registrations = new LinkedHashMap<>();
        try {
            if (settings.isGms()) {
                registerGameMode(dispatcher, registrations, "gms", GameType.SURVIVAL);
            }
            if (settings.isGmsp()) {
                registerGameMode(dispatcher, registrations, "gmsp", GameType.SPECTATOR);
            }
            if (settings.isGmc()) {
                registerGameMode(dispatcher, registrations, "gmc", GameType.CREATIVE);
            }
            if (settings.isGive()) {
                CommandNode<CommandSourceStack> vanillaGive = dispatcher.getRoot().getChild("give");
                Handler shorthand = playerOnly("/give <item> <amount>", this::give);
                register(dispatcher, registrations, "give", true,
                        (CommandSourceStack source, String[] args) -> giveOrVanilla(vanillaGive, shorthand, source, args),
                        (CommandSourceStack source) -> vanillaApplies(vanillaGive, source, null)
                                || permitted("react.shorthands.give;minecraft.command.give").test(source),
                        (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> completeGiveOrVanilla(vanillaGive, context, builder));
                if (vanillaGive != null && dispatcher.getRoot().getChild("minecraft:give") == null) {
                    registerVanillaAlias(dispatcher, registrations, vanillaGive);
                }
            }
            if (settings.isMore()) {
                register(dispatcher, registrations, "more", true, playerOnly("/more", this::more), permitted("react.shorthands.more"), null);
            }
            if (settings.isRl()) {
                register(dispatcher, registrations, "rl", true, playerOnly("/rl", this::reload),
                        permitted("react.shorthands.rl;minecraft.command.reload;bukkit.command.reload"), null);
            }
            registerCustomCommands(dispatcher, registrations, settings);
        } catch (RuntimeException | Error failure) {
            unregister(dispatcher, registrations);
            throw failure;
        }
        refreshPlayerCommands();
        return () -> {
            unregister(dispatcher, registrations);
            refreshPlayerCommands();
        };
    }

    private void registerGameMode(CommandDispatcher<CommandSourceStack> dispatcher, Map<String, Registration> registrations, String label,
                                  GameType mode) {
        String modeName = mode.name().toLowerCase(Locale.ROOT);
        register(dispatcher, registrations, label, true, playerOnly("/" + label, (ServerPlayer player, String[] args, CommandSourceStack source) -> {
            if (args.length != 0) {
                usage(source, "/" + label);
                return;
            }
            player.setGameMode(mode);
            ReactLanguage.send(new ModCommandSender(source), ShorthandMessages.GAME_MODE_SET, MessageArgument.untrusted("mode", modeName));
        }), permitted("react.shorthands." + label + ";minecraft.command.gamemode"), null);
    }

    private void registerCustomCommands(CommandDispatcher<CommandSourceStack> dispatcher, Map<String, Registration> registrations,
                                        TweakShorthands settings) {
        Map<String, TweakShorthands.CustomShorthand> customCommands = settings.getCustomCommands();
        if (customCommands == null || customCommands.isEmpty()) {
            return;
        }
        for (Map.Entry<String, TweakShorthands.CustomShorthand> entry : customCommands.entrySet()) {
            String label = normalizeCustomLabel(entry.getKey());
            TweakShorthands.CustomShorthand definition = entry.getValue();
            if (label == null) {
                ReactPlatforms.get().warn("Skipped custom shorthand with invalid label: " + entry.getKey());
                continue;
            }
            if (definition == null || !definition.isEnabled()) {
                continue;
            }
            String commandTemplate = normalizeCommandTemplate(definition.getCommand());
            if (commandTemplate == null) {
                ReactPlatforms.get().warn("Skipped custom shorthand /" + label + " because its command is blank or invalid.");
                continue;
            }
            String permission = definition.getPermission() == null || definition.getPermission().isBlank()
                    ? null : definition.getPermission().strip();
            register(dispatcher, registrations, label, definition.isOverrideExisting(),
                    (CommandSourceStack source, String[] args) -> dispatchCustom(label, commandTemplate, source, args),
                    permission == null ? (CommandSourceStack source) -> true : permitted(permission),
                    (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> completeCustom(label, commandTemplate, context, builder));
        }
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher, Map<String, Registration> registrations, String label,
                          boolean overrideExisting, Handler handler, Predicate<CommandSourceStack> permission, Completer completer) {
        String namespacedLabel = COMMAND_NAMESPACE + ":" + label;
        if (registrations.containsKey(label)) {
            ReactPlatforms.get().warn("Skipped duplicate shorthand command /" + label);
            return;
        }
        RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();
        if (!overrideExisting && (root.getChild(label) != null || root.getChild(namespacedLabel) != null)) {
            ReactPlatforms.get().warn("Skipped custom shorthand /" + label + " because that command label is already registered.");
            return;
        }
        CommandNode<CommandSourceStack> previousDirect = removeChild(root, label);
        CommandNode<CommandSourceStack> previousNamespaced = removeChild(root, namespacedLabel);
        try {
            CommandNode<CommandSourceStack> node = dispatcher.register(literal(label, handler, permission, completer));
            CommandNode<CommandSourceStack> namespaced = dispatcher.register(literal(namespacedLabel, handler, permission, completer));
            registrations.put(label, new Registration(label, node, namespaced, previousDirect, previousNamespaced));
            ReactPlatforms.get().verbose("Registered shorthand command /" + label);
        } catch (RuntimeException | Error failure) {
            removeChild(root, label);
            removeChild(root, namespacedLabel);
            restore(root, previousDirect);
            restore(root, previousNamespaced);
            ReactPlatforms.get().reportError("Failed to register shorthand command /" + label + ": " + failure.getMessage(), failure);
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> literal(String label, Handler handler, Predicate<CommandSourceStack> permission,
                                                                      Completer completer) {
        return Commands.literal(label)
                .executes((CommandContext<CommandSourceStack> context) -> {
                    run(context.getSource(), handler, permission, new String[0]);
                    return 1;
                })
                .then(Commands.argument(ARGUMENTS, StringArgumentType.greedyString())
                        .suggests((CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) ->
                                completer == null || !permission.test(context.getSource()) ? builder.buildFuture() : completer.complete(context, builder))
                        .executes((CommandContext<CommandSourceStack> context) -> {
                            String input = StringArgumentType.getString(context, ARGUMENTS);
                            run(context.getSource(), handler, permission, input.isEmpty() ? new String[0] : input.split(" ", -1));
                            return 1;
                        }));
    }

    /** Bukkit's command permission check: a sender without it gets the server's no-permission message. */
    private static void run(CommandSourceStack source, Handler handler, Predicate<CommandSourceStack> permission, String[] args) {
        if (!permission.test(source)) {
            source.sendSystemMessage(Component.literal(NO_PERMISSION).withStyle(ChatFormatting.RED));
            return;
        }
        handler.run(source, args);
    }

    private void unregister(CommandDispatcher<CommandSourceStack> dispatcher, Map<String, Registration> registrations) {
        RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();
        for (Registration registration : registrations.values()) {
            if (root.getChild(registration.label()) == registration.node()) {
                removeChild(root, registration.label());
                restore(root, registration.previousDirect());
            }
            String namespacedLabel = COMMAND_NAMESPACE + ":" + registration.label();
            if (root.getChild(namespacedLabel) == registration.namespaced()) {
                removeChild(root, namespacedLabel);
                restore(root, registration.previousNamespaced());
            }
        }
        registrations.clear();
    }

    private static void restore(RootCommandNode<CommandSourceStack> root, CommandNode<CommandSourceStack> previous) {
        if (previous != null && root.getChild(previous.getName()) == null) {
            root.addChild(previous);
        }
    }

    @SuppressWarnings("unchecked")
    private static CommandNode<CommandSourceStack> removeChild(CommandNode<CommandSourceStack> parent, String name) {
        CommandNode<CommandSourceStack> existing = parent.getChild(name);
        if (existing == null) {
            return null;
        }
        for (String mapName : List.of("children", "literals", "arguments")) {
            try {
                Field field = CommandNode.class.getDeclaredField(mapName);
                field.setAccessible(true);
                ((Map<String, ?>) field.get(parent)).remove(name);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                throw new IllegalStateException("Could not remove command node /" + name, failure);
            }
        }
        return existing;
    }

    private void refreshPlayerCommands() {
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            server.getCommands().sendCommands(player);
        }
    }

    @FunctionalInterface
    private interface PlayerHandler {
        void run(ServerPlayer player, String[] args, CommandSourceStack source);
    }

    private static Handler playerOnly(String usage, PlayerHandler handler) {
        return (CommandSourceStack source, String[] args) -> {
            if (!(source.getEntity() instanceof ServerPlayer)) {
                ReactLanguage.send(new ModCommandSender(source), ShorthandMessages.PLAYER_ONLY);
                return;
            }
            handler.run((ServerPlayer) source.getEntity(), args, source);
        };
    }

    private static Predicate<CommandSourceStack> permitted(String nodes) {
        return (CommandSourceStack source) -> {
            for (String node : nodes.split(";")) {
                if (!node.isBlank() && ModPermissions.has(source, node.strip())) {
                    return true;
                }
            }
            return false;
        };
    }

    private static void usage(CommandSourceStack source, String usage) {
        ReactLanguage.send(new ModCommandSender(source), ShorthandMessages.USAGE, MessageArgument.untrusted("usage", usage));
    }

    private void give(ServerPlayer player, String[] args, CommandSourceStack source) {
        if (args.length != 2) {
            usage(source, "/give <item> <amount>");
            return;
        }
        Item item = resolveItem(args[0]);
        if (item == null || !new ItemStack(item).isItemEnabled(VanillaApi.level(player).enabledFeatures())) {
            ReactLanguage.send(new ModCommandSender(source), ShorthandMessages.ITEM_UNAVAILABLE, MessageArgument.untrusted("item", args[0]));
            return;
        }
        int amount = parsePositiveAmount(args[1]);
        int maxStackSize = Math.max(1, new ItemStack(item).getMaxStackSize());
        int maxAmount = maxStackSize * MAX_GIVE_STACKS;
        if (amount < 1 || amount > maxAmount) {
            ReactLanguage.send(new ModCommandSender(source), ShorthandMessages.AMOUNT_OUT_OF_RANGE, MessageArgument.untrusted("maximum", maxAmount));
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(maxStackSize, remaining);
            stacks.add(new ItemStack(item, stackAmount));
            remaining -= stackAmount;
        }
        giveItems(player, stacks);
        ReactLanguage.send(new ModCommandSender(source), ShorthandMessages.ITEM_GIVEN, MessageArgument.untrusted("amount", amount),
                MessageArgument.untrusted("item", itemKey(item)));
    }

    /**
     * True when the arguments are vanilla's {@code give <targets> <item> [count]} rather than the shorthand's
     * {@code give <item> <amount>}: sent by a non-player, with a selector or player name first, or with another argument
     * count. Vanilla's own node then answers, as {@code minecraft:give} does on Bukkit.
     */
    private boolean vanillaApplies(CommandNode<CommandSourceStack> vanillaGive, CommandSourceStack source, String[] args) {
        if (vanillaGive == null) {
            return false;
        }
        if (!(source.getEntity() instanceof ServerPlayer)) {
            return true;
        }
        if (args == null || args.length == 0) {
            return false;
        }
        return args.length != 2 || args[0].startsWith("@") || server.getPlayerList().getPlayerByName(args[0]) != null;
    }

    private void giveOrVanilla(CommandNode<CommandSourceStack> vanillaGive, Handler shorthand, CommandSourceStack source, String[] args) {
        if (!vanillaApplies(vanillaGive, source, args)) {
            shorthand.run(source, args);
            return;
        }
        CommandDispatcher<CommandSourceStack> vanilla = new CommandDispatcher<>();
        vanilla.getRoot().addChild(vanillaGive);
        server.getCommands().performCommand(vanilla.parse("give" + (args.length == 0 ? "" : " " + String.join(" ", args)), source),
                "give " + String.join(" ", args));
    }

    private CompletableFuture<Suggestions> completeGiveOrVanilla(CommandNode<CommandSourceStack> vanillaGive, CommandContext<CommandSourceStack> context,
                                                                 SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String[] args = remaining.split(" ", -1);
        boolean vanillaShape = vanillaGive != null && (args[0].startsWith("@") || server.getPlayerList().getPlayerByName(args[0]) != null
                || !(context.getSource().getEntity() instanceof ServerPlayer));
        if (!vanillaShape) {
            return completeGive(context, builder);
        }
        CommandDispatcher<CommandSourceStack> vanilla = new CommandDispatcher<>();
        vanilla.getRoot().addChild(vanillaGive);
        String line = "give " + remaining;
        return vanilla.getCompletionSuggestions(vanilla.parse(line, context.getSource())).thenApply((Suggestions suggestions) -> {
            SuggestionsBuilder shifted = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
            for (Suggestion suggestion : suggestions.getList()) {
                shifted.suggest(suggestion.getText());
            }
            return shifted.build();
        });
    }

    /** Keeps vanilla's give reachable as {@code minecraft:give} while the shorthand owns {@code give}, as on Bukkit. */
    private void registerVanillaAlias(CommandDispatcher<CommandSourceStack> dispatcher, Map<String, Registration> registrations,
                                      CommandNode<CommandSourceStack> vanillaGive) {
        LiteralArgumentBuilder<CommandSourceStack> alias = Commands.literal("minecraft:give").requires(vanillaGive.getRequirement());
        for (CommandNode<CommandSourceStack> child : vanillaGive.getChildren()) {
            alias.then(child);
        }
        CommandNode<CommandSourceStack> node = dispatcher.register(alias);
        registrations.put("minecraft:give", new Registration("minecraft:give", node, node, null, null));
    }

    private void more(ServerPlayer player, String[] args, CommandSourceStack source) {
        if (args.length != 0) {
            usage(source, "/more");
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ReactLanguage.send(new ModCommandSender(source), ShorthandMessages.MORE_EMPTY_HAND);
            return;
        }
        giveItems(player, List.of(held.copyWithCount(Math.max(1, held.getMaxStackSize()))));
        ReactLanguage.send(new ModCommandSender(source), ShorthandMessages.MORE_GIVEN);
    }

    private void reload(ServerPlayer player, String[] args, CommandSourceStack source) {
        if (args.length != 0) {
            usage(source, "/rl");
            return;
        }
        if (server.getCommands().getDispatcher().getRoot().getChild("reload") == null) {
            ReactLanguage.send(new ModCommandSender(source), ShorthandMessages.RELOAD_UNAVAILABLE);
            return;
        }
        server.getCommands().performPrefixedCommand(source, "reload");
    }

    private static void giveItems(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(VanillaApi.level(player), player.getX(), player.getY(), player.getZ(), stack);
            }
        }
    }

    private CompletableFuture<Suggestions> completeGive(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String[] args = remaining.split(" ", -1);
        SuggestionsBuilder last = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String key : itemKeys()) {
                String path = key.substring(key.indexOf(':') + 1);
                if (key.startsWith(prefix) || (!prefix.contains(":") && path.startsWith(prefix))) {
                    last.suggest(key);
                }
            }
        } else if (args.length == 2) {
            Item item = resolveItem(args[0]);
            if (item != null) {
                String maximum = Integer.toString(Math.max(1, new ItemStack(item).getMaxStackSize()));
                List<String> candidates = "1".equals(maximum) ? List.of("1") : List.of("1", maximum);
                for (String candidate : candidates) {
                    if (candidate.startsWith(args[1])) {
                        last.suggest(candidate);
                    }
                }
            }
        }
        return last.buildFuture();
    }

    private static List<String> itemKeys() {
        List<String> keys = new ArrayList<>();
        for (Object key : BuiltInRegistries.ITEM.keySet()) {
            String text = key.toString();
            if (!"minecraft:air".equals(text)) {
                keys.add(text);
            }
        }
        Collections.sort(keys);
        return keys;
    }

    private static Item resolveItem(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String key = input.toLowerCase(Locale.ROOT);
        if (!key.contains(":")) {
            key = "minecraft:" + key;
        }
        if ("minecraft:air".equals(key)) {
            return null;
        }
        Item item = VanillaApi.itemOrAir(key);
        return item == null || item == Items.AIR ? null : item;
    }

    private static String itemKey(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private void dispatchCustom(String label, String commandTemplate, CommandSourceStack source, String[] args) {
        ModCommandSender sender = new ModCommandSender(source);
        if (!activeCustomAliases.get().add(label)) {
            ReactLanguage.send(sender, ShorthandMessages.RECURSIVE_STOPPED, MessageArgument.untrusted("label", label));
            return;
        }
        try {
            String commandLine = buildCommandLine(commandTemplate, args);
            String command = commandLine.split(" ", 2)[0];
            if (commandLine.isBlank() || server.getCommands().getDispatcher().getRoot().getChild(command) == null) {
                ReactLanguage.send(sender, ShorthandMessages.CONFIGURED_UNAVAILABLE, MessageArgument.untrusted("label", label));
                return;
            }
            server.getCommands().performPrefixedCommand(source, commandLine);
        } catch (RuntimeException failure) {
            ReactLanguage.send(sender, ShorthandMessages.CONFIGURED_FAILED, MessageArgument.untrusted("label", label));
            ReactPlatforms.get().reportError("Custom shorthand /" + label + " failed: " + failure.getMessage(), failure);
        } finally {
            exitCustomAlias(label);
        }
    }

    private CompletableFuture<Suggestions> completeCustom(String label, String commandTemplate, CommandContext<CommandSourceStack> context,
                                                          SuggestionsBuilder builder) {
        if (!activeCustomAliases.get().add(label)) {
            return builder.buildFuture();
        }
        try {
            String remaining = builder.getRemaining();
            String[] args = remaining.isEmpty() ? new String[0] : remaining.split(" ", -1);
            String commandLine = buildCompletionLine(commandTemplate, args);
            if (commandLine.isBlank()) {
                return builder.buildFuture();
            }
            CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
            Suggestions delegated = dispatcher.getCompletionSuggestions(dispatcher.parse(commandLine, context.getSource())).join();
            SuggestionsBuilder last = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
            for (Suggestion suggestion : delegated.getList()) {
                last.suggest(suggestion.getText());
            }
            return last.buildFuture();
        } catch (RuntimeException failure) {
            return builder.buildFuture();
        } finally {
            exitCustomAlias(label);
        }
    }

    private void exitCustomAlias(String label) {
        Set<String> active = activeCustomAliases.get();
        active.remove(label);
        if (active.isEmpty()) {
            activeCustomAliases.remove();
        }
    }

    private static String buildCommandLine(String commandTemplate, String[] args) {
        String arguments = String.join(" ", args);
        if (commandTemplate.contains("{args}")) {
            return commandTemplate.replace("{args}", arguments);
        }
        if (args.length == 0) {
            return commandTemplate;
        }
        return commandTemplate + " " + arguments;
    }

    private static String buildCompletionLine(String commandTemplate, String[] args) {
        int placeholder = commandTemplate.indexOf("{args}");
        if (placeholder < 0) {
            return buildCommandLine(commandTemplate, args);
        }
        return commandTemplate.substring(0, placeholder) + String.join(" ", args);
    }

    private static String normalizeCustomLabel(String configuredLabel) {
        if (configuredLabel == null) {
            return null;
        }
        String label = configuredLabel.strip().toLowerCase(Locale.ROOT);
        while (label.startsWith("/")) {
            label = label.substring(1);
        }
        return COMMAND_LABEL.matcher(label).matches() ? label : null;
    }

    private static String normalizeCommandTemplate(String configuredCommand) {
        if (configuredCommand == null) {
            return null;
        }
        String command = configuredCommand.strip();
        while (command.startsWith("/")) {
            command = command.substring(1).stripLeading();
        }
        if (command.isBlank() || command.contains("\n") || command.contains("\r")) {
            return null;
        }
        return command;
    }

    private static int parsePositiveAmount(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
