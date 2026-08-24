/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.content.tweak;

import art.arcane.react.React;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.ShorthandMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.FeatureFlag;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import java.util.regex.Pattern;

final class ShorthandCommandService {
  private static final String COMMAND_NAMESPACE = "react";
  private static final String AIR_KEY = "minecraft:air";
  private static final int MAX_GIVE_STACKS = 100;
  private static final Pattern COMMAND_LABEL = Pattern.compile("[a-z0-9][a-z0-9_-]*");

  private final TweakShorthands settings;
  private final CommandMap commandMap;
  private final ItemCatalog itemCatalog;
  private final Map<String, CommandRegistration> registrations;
  private final ThreadLocal<Set<String>> activeCustomAliases;
  private final Set<String> reportedCompletionFailures;

  ShorthandCommandService(TweakShorthands settings, CommandMap commandMap, ItemCatalog itemCatalog) {
    this.settings = Objects.requireNonNull(settings);
    this.commandMap = Objects.requireNonNull(commandMap);
    this.itemCatalog = Objects.requireNonNull(itemCatalog);
    this.registrations = new LinkedHashMap<>();
    this.activeCustomAliases = ThreadLocal.withInitial(HashSet::new);
    this.reportedCompletionFailures = ConcurrentHashMap.newKeySet();
  }

  static ShorthandCommandService create(TweakShorthands settings) {
    Registry<ItemType> itemRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ITEM);
    return new ShorthandCommandService(settings, Bukkit.getCommandMap(), ItemCatalog.fromRegistry(itemRegistry));
  }

  void register() {
    synchronized (commandMap) {
      registerConfiguredCommands();
    }
    refreshPlayerCommands();
  }

  private void registerConfiguredCommands() {
    if (settings.isGms()) {
      registerCommand(new GameModeShorthandCommand("gms", GameMode.SURVIVAL), true);
    }
    if (settings.isGmsp()) {
      registerCommand(new GameModeShorthandCommand("gmsp", GameMode.SPECTATOR), true);
    }
    if (settings.isGmc()) {
      registerCommand(new GameModeShorthandCommand("gmc", GameMode.CREATIVE), true);
    }
    if (settings.isGive()) {
      registerCommand(new GiveShorthandCommand(itemCatalog), true);
    }
    if (settings.isMore()) {
      registerCommand(new MoreShorthandCommand(), true);
    }
    if (settings.isRl()) {
      registerCommand(new ReloadShorthandCommand(), true);
    }
    registerCustomCommands();
  }

  void unregister() {
    synchronized (commandMap) {
      if (registrations.isEmpty()) {
        return;
      }

      Map<String, Command> knownCommands = commandMap.getKnownCommands();
      for (CommandRegistration registration : registrations.values()) {
        restoreMapping(knownCommands, registration.label(), registration.command(), registration.previousDirect());
        restoreMapping(knownCommands, registration.namespacedLabel(), registration.command(), registration.previousNamespaced());
        registration.command().unregister(commandMap);
      }
      registrations.clear();
    }
    refreshPlayerCommands();
  }

  private void registerCustomCommands() {
    Map<String, TweakShorthands.CustomShorthand> customCommands = settings.getCustomCommands();
    if (customCommands == null || customCommands.isEmpty()) {
      return;
    }

    for (Map.Entry<String, TweakShorthands.CustomShorthand> entry : customCommands.entrySet()) {
      String label = normalizeCustomLabel(entry.getKey());
      TweakShorthands.CustomShorthand definition = entry.getValue();
      if (label == null) {
        React.warn("Skipped custom shorthand with invalid label: " + entry.getKey());
        continue;
      }
      if (definition == null || !definition.isEnabled()) {
        continue;
      }

      String commandTemplate = normalizeCommandTemplate(definition.getCommand());
      if (commandTemplate == null) {
        React.warn("Skipped custom shorthand /" + label + " because its command is blank or invalid.");
        continue;
      }

      ConfigurableCommandOptions options = new ConfigurableCommandOptions(
          label,
          commandTemplate,
          normalizePermission(definition.getPermission())
      );
      registerCommand(new ConfigurableShorthandCommand(this, options), definition.isOverrideExisting());
    }
  }

  private void registerCommand(Command command, boolean overrideExisting) {
    String label = command.getName().toLowerCase(Locale.ROOT);
    String namespacedLabel = COMMAND_NAMESPACE + ":" + label;
    if (registrations.containsKey(label)) {
      React.warn("Skipped duplicate shorthand command /" + label);
      return;
    }

    Map<String, Command> knownCommands = commandMap.getKnownCommands();
    if (!overrideExisting && (knownCommands.containsKey(label) || knownCommands.containsKey(namespacedLabel))) {
      React.warn("Skipped custom shorthand /" + label + " because that command label is already registered.");
      return;
    }

    Command previousDirect = knownCommands.remove(label);
    Command previousNamespaced = knownCommands.remove(namespacedLabel);

    try {
      boolean registered = commandMap.register(label, COMMAND_NAMESPACE, command);
      if (!registered || knownCommands.get(label) != command) {
        throw new IllegalStateException("Command map rejected /" + label);
      }

      registrations.put(label, new CommandRegistration(
          label,
          namespacedLabel,
          command,
          previousDirect,
          previousNamespaced
      ));
      React.verbose("Registered shorthand command /" + label);
    } catch (Throwable throwable) {
      knownCommands.remove(label, command);
      knownCommands.remove(namespacedLabel, command);
      command.unregister(commandMap);
      restoreIfMissing(knownCommands, label, previousDirect);
      restoreIfMissing(knownCommands, namespacedLabel, previousNamespaced);
      React.reportError("Failed to register shorthand command /" + label
          + ": " + throwable.getMessage(), throwable);
    }
  }

  private void dispatchCustom(String label, String commandTemplate, CommandSender sender, String[] args) {
    if (!enterCustomAlias(label)) {
      ReactLanguage.send(
          sender,
          ShorthandMessages.RECURSIVE_STOPPED,
          MessageArgument.untrusted("label", label)
      );
      return;
    }

    try {
      String commandLine = buildCommandLine(commandTemplate, args);
      if (commandLine.isBlank() || !commandMap.dispatch(sender, commandLine)) {
        ReactLanguage.send(
            sender,
            ShorthandMessages.CONFIGURED_UNAVAILABLE,
            MessageArgument.untrusted("label", label)
        );
      }
    } catch (Throwable throwable) {
      ReactLanguage.send(
          sender,
          ShorthandMessages.CONFIGURED_FAILED,
          MessageArgument.untrusted("label", label)
      );
      React.reportError("Custom shorthand /" + label + " failed: "
          + throwable.getMessage(), throwable);
    } finally {
      exitCustomAlias(label);
    }
  }

  private List<String> completeCustom(String label, String commandTemplate, CommandSender sender, String[] args) {
    if (!enterCustomAlias(label)) {
      return List.of();
    }

    try {
      String commandLine = buildCompletionLine(commandTemplate, args);
      if (commandLine.isBlank()) {
        return List.of();
      }
      List<String> completions = commandMap.tabComplete(sender, commandLine);
      return completions == null ? List.of() : completions;
    } catch (Throwable throwable) {
      if (reportedCompletionFailures.add(label)) {
        React.reportError("Tab completion failed for custom shorthand /" + label
            + ": " + throwable.getMessage(), throwable);
      }
      return List.of();
    } finally {
      exitCustomAlias(label);
    }
  }

  private boolean enterCustomAlias(String label) {
    return activeCustomAliases.get().add(label);
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

  private static String normalizePermission(String configuredPermission) {
    return configuredPermission == null || configuredPermission.isBlank()
        ? null
        : configuredPermission.strip();
  }

  private void refreshPlayerCommands() {
    Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
    if (onlinePlayers.isEmpty()) {
      return;
    }

    List<Player> players = new ArrayList<>(onlinePlayers);
    if (!J.isFoliaThreading()) {
      for (Player player : players) {
        player.updateCommands();
      }
      return;
    }

    for (Player player : players) {
      FoliaScheduler.runEntity(React.instance, player, player::updateCommands, 0, null);
    }
  }

  private static void restoreMapping(Map<String, Command> knownCommands, String label, Command owned, Command previous) {
    if (!knownCommands.remove(label, owned)) {
      return;
    }
    restoreIfMissing(knownCommands, label, previous);
  }

  private static void restoreIfMissing(Map<String, Command> knownCommands, String label, Command previous) {
    if (previous == null) {
      return;
    }
    if (previous instanceof PluginIdentifiableCommand identifiable
        && !identifiable.getPlugin().isEnabled()) {
      return;
    }
    knownCommands.putIfAbsent(label, previous);
  }

  private static void giveItems(Player player, ItemStack[] items) {
    Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items);
    if (leftovers.isEmpty()) {
      return;
    }

    Location dropLocation = player.getLocation();
    World world = player.getWorld();
    for (ItemStack leftover : leftovers.values()) {
      world.dropItemNaturally(dropLocation, leftover);
    }
  }

  private static ItemStack[] createStacks(CatalogItem item, int amount) {
    int maxStackSize = Math.max(1, item.maxStackSize());
    int stackCount = (amount + maxStackSize - 1) / maxStackSize;
    ItemStack[] stacks = new ItemStack[stackCount];
    int remaining = amount;
    for (int i = 0; i < stackCount; i++) {
      int stackAmount = Math.min(maxStackSize, remaining);
      stacks[i] = item.createStack(stackAmount);
      remaining -= stackAmount;
    }
    return stacks;
  }

  private static int parsePositiveAmount(String input) {
    try {
      return Integer.parseInt(input);
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }

  private abstract static class ShorthandCommand extends Command {
    protected ShorthandCommand(String name, String description, String usage, String permission) {
      super(name, description, usage, List.of());
      setPermission(permission);
    }

    @Override
    public final boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
      if (!testPermission(sender)) {
        return true;
      }
      if (!(sender instanceof Player player)) {
        ReactLanguage.send(sender, ShorthandMessages.PLAYER_ONLY);
        return true;
      }

      execute(player, args);
      return true;
    }

    @Override
    public final @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
      if (!(sender instanceof Player player) || !testPermissionSilent(sender)) {
        return List.of();
      }
      return tabComplete(player, args);
    }

    protected abstract void execute(Player player, String[] args);

    protected List<String> tabComplete(Player player, String[] args) {
      return List.of();
    }

    protected void sendUsage(Player player) {
      ReactLanguage.send(
          player,
          ShorthandMessages.USAGE,
          MessageArgument.untrusted("usage", getUsage())
      );
    }
  }

  private static final class GameModeShorthandCommand extends ShorthandCommand {
    private final GameMode gameMode;

    private GameModeShorthandCommand(String name, GameMode gameMode) {
      super(
          name,
          ReactLanguage.plain(
              ShorthandMessages.GAME_MODE_DESCRIPTION,
              MessageArgument.untrusted("mode", gameMode.name().toLowerCase(Locale.ROOT))
          ),
          "/" + name,
          "react.shorthands." + name + ";minecraft.command.gamemode"
      );
      this.gameMode = gameMode;
    }

    @Override
    protected void execute(Player player, String[] args) {
      if (args.length != 0) {
        sendUsage(player);
        return;
      }

      player.setGameMode(gameMode);
      String modeName = gameMode.name().toLowerCase(Locale.ROOT);
      ReactLanguage.send(
          player,
          ShorthandMessages.GAME_MODE_SET,
          MessageArgument.untrusted("mode", modeName)
      );
    }
  }

  private static final class GiveShorthandCommand extends ShorthandCommand {
    private final ItemCatalog itemCatalog;

    private GiveShorthandCommand(ItemCatalog itemCatalog) {
      super(
          "give",
          ReactLanguage.plain(ShorthandMessages.GIVE_DESCRIPTION),
          "/give <item> <amount>",
          "react.shorthands.give;minecraft.command.give"
      );
      this.itemCatalog = itemCatalog;
    }

    @Override
    protected void execute(Player player, String[] args) {
      if (args.length != 2) {
        sendUsage(player);
        return;
      }

      CatalogItem item = itemCatalog.resolve(args[0]);
      if (item == null || !player.getWorld().getFeatureFlags().containsAll(item.requiredFeatures())) {
        ReactLanguage.send(
            player,
            ShorthandMessages.ITEM_UNAVAILABLE,
            MessageArgument.untrusted("item", args[0])
        );
        return;
      }

      int amount = parsePositiveAmount(args[1]);
      int maxAmount = Math.max(1, item.maxStackSize()) * MAX_GIVE_STACKS;
      if (amount < 1 || amount > maxAmount) {
        ReactLanguage.send(
            player,
            ShorthandMessages.AMOUNT_OUT_OF_RANGE,
            MessageArgument.untrusted("maximum", maxAmount)
        );
        return;
      }

      giveItems(player, createStacks(item, amount));
      ReactLanguage.send(
          player,
          ShorthandMessages.ITEM_GIVEN,
          MessageArgument.untrusted("amount", amount),
          MessageArgument.untrusted("item", item.key())
      );
    }

    @Override
    protected List<String> tabComplete(Player player, String[] args) {
      if (args.length == 1) {
        return itemCatalog.complete(args[0]);
      }
      if (args.length == 2) {
        CatalogItem item = itemCatalog.resolve(args[0]);
        if (item == null) {
          return List.of();
        }
        return completeAmounts(args[1], item.maxStackSize());
      }
      return List.of();
    }

    private List<String> completeAmounts(String input, int maxStackSize) {
      List<String> candidates = List.of("1", Integer.toString(Math.max(1, maxStackSize)));
      if (input == null || input.isEmpty()) {
        return candidates.stream().distinct().toList();
      }

      List<String> matches = new ArrayList<>(candidates.size());
      for (String candidate : candidates) {
        if (candidate.startsWith(input) && !matches.contains(candidate)) {
          matches.add(candidate);
        }
      }
      return matches;
    }
  }

  private static final class MoreShorthandCommand extends ShorthandCommand {
    private MoreShorthandCommand() {
      super(
          "more",
          ReactLanguage.plain(ShorthandMessages.MORE_DESCRIPTION),
          "/more",
          "react.shorthands.more"
      );
    }

    @Override
    protected void execute(Player player, String[] args) {
      if (args.length != 0) {
        sendUsage(player);
        return;
      }

      ItemStack held = player.getInventory().getItemInMainHand();
      if (held.isEmpty()) {
        ReactLanguage.send(player, ShorthandMessages.MORE_EMPTY_HAND);
        return;
      }

      int amount = Math.max(1, held.getMaxStackSize());
      ItemStack exactCopy = held.asQuantity(amount);
      giveItems(player, new ItemStack[]{exactCopy});
      ReactLanguage.send(player, ShorthandMessages.MORE_GIVEN);
    }
  }

  private static final class ReloadShorthandCommand extends ShorthandCommand {
    private ReloadShorthandCommand() {
      super(
          "rl",
          ReactLanguage.plain(ShorthandMessages.RELOAD_DESCRIPTION),
          "/rl",
          "react.shorthands.rl;minecraft.command.reload;bukkit.command.reload"
      );
    }

    @Override
    protected void execute(Player player, String[] args) {
      if (args.length != 0) {
        sendUsage(player);
        return;
      }

      if (!Bukkit.dispatchCommand(player, "reload")) {
        ReactLanguage.send(player, ShorthandMessages.RELOAD_UNAVAILABLE);
      }
    }
  }

  private static final class ConfigurableShorthandCommand extends Command {
    private final ShorthandCommandService service;
    private final String commandTemplate;

    private ConfigurableShorthandCommand(ShorthandCommandService service, ConfigurableCommandOptions options) {
      super(
          options.label(),
          "Runs an operator-configured shorthand command.",
          "/" + options.label() + " [arguments...]",
          List.of()
      );
      this.service = service;
      this.commandTemplate = options.commandTemplate();
      setPermission(options.permission());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
      if (!testPermission(sender)) {
        return true;
      }
      service.dispatchCustom(getName(), commandTemplate, sender, args);
      return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
      if (!testPermissionSilent(sender)) {
        return List.of();
      }
      return service.completeCustom(getName(), commandTemplate, sender, args);
    }
  }

  static final class ItemCatalog {
    private final Map<String, CatalogItem> items;
    private final List<String> keys;

    private ItemCatalog(Collection<CatalogItem> catalogItems) {
      Map<String, CatalogItem> items = new HashMap<>();
      for (CatalogItem item : catalogItems) {
        if (!AIR_KEY.equals(item.key())) {
          items.put(item.key(), item);
        }
      }

      List<String> keys = new ArrayList<>(items.keySet());
      Collections.sort(keys);
      this.items = Map.copyOf(items);
      this.keys = List.copyOf(keys);
    }

    static ItemCatalog of(CatalogItem... items) {
      return new ItemCatalog(List.of(items));
    }

    private static ItemCatalog fromRegistry(Registry<ItemType> registry) {
      List<CatalogItem> items = new ArrayList<>();
      for (ItemType itemType : registry) {
        NamespacedKey key = itemType.getKey();
        String keyString = key.asString();
        if (!AIR_KEY.equals(keyString)) {
          items.add(new CatalogItem(
              keyString,
              itemType.getMaxStackSize(),
              itemType.requiredFeatures(),
              itemType::createItemStack
          ));
        }
      }
      return new ItemCatalog(items);
    }

    private CatalogItem resolve(String input) {
      if (input == null || input.isBlank()) {
        return null;
      }

      NamespacedKey key = NamespacedKey.fromString(input.toLowerCase(Locale.ROOT));
      return key == null ? null : items.get(key.asString());
    }

    private List<String> complete(String input) {
      String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
      List<String> matches = new ArrayList<>();
      for (String key : keys) {
        String path = key.substring(key.indexOf(':') + 1);
        if (key.startsWith(prefix) || (!prefix.contains(":") && path.startsWith(prefix))) {
          matches.add(key);
        }
      }
      return matches;
    }
  }

  record CatalogItem(
      String key,
      int maxStackSize,
      Set<FeatureFlag> requiredFeatures,
      IntFunction<ItemStack> stackFactory
  ) {
    CatalogItem {
      Objects.requireNonNull(key);
      Objects.requireNonNull(requiredFeatures);
      Objects.requireNonNull(stackFactory);
    }

    ItemStack createStack(int amount) {
      return stackFactory.apply(amount);
    }
  }

  private record ConfigurableCommandOptions(
      String label,
      String commandTemplate,
      String permission
  ) {
  }

  private record CommandRegistration(
      String label,
      String namespacedLabel,
      Command command,
      Command previousDirect,
      Command previousNamespaced
  ) {
  }
}
