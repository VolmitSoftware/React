package art.arcane.react.content.tweak;

import art.arcane.react.util.project.config.TomlCodec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TweakShorthandsTest {
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

  @Test
  public void shorthandsAreOffByDefaultWithEveryRequestedCommandSelected() {
    TweakShorthands tweak = new TweakShorthands();

    Assertions.assertEquals("shorthands", tweak.getId());
    Assertions.assertFalse(tweak.isEnabled());
    Assertions.assertTrue(tweak.isGms());
    Assertions.assertTrue(tweak.isGmsp());
    Assertions.assertTrue(tweak.isGmc());
    Assertions.assertTrue(tweak.isGive());
    Assertions.assertTrue(tweak.isMore());
    Assertions.assertTrue(tweak.isRl());
    Assertions.assertTrue(tweak.getCustomCommands().isEmpty());
  }

  @Test
  public void customShorthandDefinitionsSurviveTomlRoundTrip() throws Exception {
    TweakShorthands tweak = new TweakShorthands();
    TweakShorthands.CustomShorthand definition = customShorthand(
        "time set {args}",
        "react.shorthands.custom.day",
        true
    );
    tweak.getCustomCommands().put("day", definition);

    String toml = TomlCodec.toToml(tweak, "tweak:shorthands");
    TweakShorthands parsed = TomlCodec.fromToml(toml, TweakShorthands.class);

    Assertions.assertTrue(toml.contains("[customCommands.day]"));
    Assertions.assertTrue(toml.contains("command = \"time set {args}\""));
    Assertions.assertEquals(1, parsed.getCustomCommands().size());
    TweakShorthands.CustomShorthand parsedDefinition = parsed.getCustomCommands().get("day");
    Assertions.assertNotNull(parsedDefinition);
    Assertions.assertTrue(parsedDefinition.isEnabled());
    Assertions.assertEquals("time set {args}", parsedDefinition.getCommand());
    Assertions.assertEquals("react.shorthands.custom.day", parsedDefinition.getPermission());
    Assertions.assertTrue(parsedDefinition.isOverrideExisting());
  }

  @Test
  public void registeredCommandsUseCodeOwnedEnglishDescriptions() throws Exception {
    TweakShorthands tweak = new TweakShorthands();
    enableOnly(tweak, "gms", "give", "more", "rl");
    Map<String, Command> knownCommands = new LinkedHashMap<>();
    ShorthandCommandService service = new ShorthandCommandService(tweak, commandMap(knownCommands), itemCatalog());

    try (MockedStatic<Bukkit> bukkit = mockBukkitWithoutPlayers()) {
      service.register();

      Assertions.assertEquals("Switches the caster to survival mode.", knownCommands.get("gms").getDescription());
      Assertions.assertEquals("Gives the caster a Minecraft item.", knownCommands.get("give").getDescription());
      Assertions.assertEquals("Gives one maximum-size copy of the exact held item.", knownCommands.get("more").getDescription());
      Assertions.assertEquals("Invokes the server's bare reload command.", knownCommands.get("rl").getDescription());
    }
  }

  @Test
  public void customShorthandsForwardArgumentsAndDelegateCompletion() throws Exception {
    TweakShorthands tweak = new TweakShorthands();
    enableOnly(tweak);
    tweak.getCustomCommands().put(
        "weather",
        customShorthand("minecraft:weather", "react.shorthands.custom", false)
    );
    Map<String, Command> knownCommands = new LinkedHashMap<>();
    CommandMap commandMap = commandMap(knownCommands);
    Mockito.when(commandMap.dispatch(Mockito.any(), Mockito.eq("minecraft:weather clear 30"))).thenReturn(true);
    Mockito.when(commandMap.tabComplete(Mockito.any(), Mockito.eq("minecraft:weather cl")))
        .thenReturn(List.of("clear"));
    ShorthandCommandService service = new ShorthandCommandService(tweak, commandMap, itemCatalog());
    Player player = permittedPlayer();

    try (MockedStatic<Bukkit> bukkit = mockBukkitWithoutPlayers()) {
      service.register();
      Command weather = knownCommands.get("weather");

      Assertions.assertNotNull(weather);
      Assertions.assertEquals("react.shorthands.custom", weather.getPermission());
      weather.execute(player, "weather", new String[]{"clear", "30"});
      Assertions.assertEquals(List.of("clear"), weather.tabComplete(player, "weather", new String[]{"cl"}));

      Mockito.verify(commandMap).dispatch(player, "minecraft:weather clear 30");
      Mockito.verify(commandMap).tabComplete(player, "minecraft:weather cl");
      service.unregister();
      Assertions.assertFalse(knownCommands.containsKey("weather"));
      Assertions.assertFalse(knownCommands.containsKey("react:weather"));
    }
  }

  @Test
  public void customArgumentPlaceholderControlsArgumentPlacement() throws Exception {
    TweakShorthands tweak = new TweakShorthands();
    enableOnly(tweak);
    tweak.getCustomCommands().put(
        "announce",
        customShorthand("say before {args} after", "", false)
    );
    Map<String, Command> knownCommands = new LinkedHashMap<>();
    CommandMap commandMap = commandMap(knownCommands);
    Mockito.when(commandMap.dispatch(Mockito.any(), Mockito.eq("say before hello world after"))).thenReturn(true);
    Mockito.when(commandMap.tabComplete(Mockito.any(), Mockito.eq("say before hel")))
        .thenReturn(List.of("hello"));
    ShorthandCommandService service = new ShorthandCommandService(tweak, commandMap, itemCatalog());
    Player player = permittedPlayer();

    try (MockedStatic<Bukkit> bukkit = mockBukkitWithoutPlayers()) {
      service.register();
      Command announce = knownCommands.get("announce");

      Assertions.assertNull(announce.getPermission());
      announce.execute(player, "announce", new String[]{"hello", "world"});
      Assertions.assertEquals(
          List.of("hello"),
          announce.tabComplete(player, "announce", new String[]{"hel"})
      );

      Mockito.verify(commandMap).dispatch(player, "say before hello world after");
      Mockito.verify(commandMap).tabComplete(player, "say before hel");
    }
  }

  @Test
  public void customShorthandsProtectExistingLabelsUnlessOverrideIsExplicit() throws Exception {
    TweakShorthands tweak = new TweakShorthands();
    enableOnly(tweak);
    TweakShorthands.CustomShorthand definition = customShorthand("spawn", "react.shorthands.custom", false);
    tweak.getCustomCommands().put("home", definition);
    Map<String, Command> knownCommands = new LinkedHashMap<>();
    Command existingHome = Mockito.mock(Command.class);
    knownCommands.put("home", existingHome);
    CommandMap commandMap = commandMap(knownCommands);

    try (MockedStatic<Bukkit> bukkit = mockBukkitWithoutPlayers()) {
      ShorthandCommandService protectedService = new ShorthandCommandService(tweak, commandMap, itemCatalog());
      protectedService.register();
      Assertions.assertSame(existingHome, knownCommands.get("home"));

      setField(definition, "overrideExisting", true);
      ShorthandCommandService overridingService = new ShorthandCommandService(tweak, commandMap, itemCatalog());
      overridingService.register();
      Assertions.assertNotSame(existingHome, knownCommands.get("home"));

      overridingService.unregister();
      Assertions.assertSame(existingHome, knownCommands.get("home"));
    }
  }

  @Test
  public void customShorthandsStopRecursiveAliases() throws Exception {
    TweakShorthands tweak = new TweakShorthands();
    enableOnly(tweak);
    tweak.getCustomCommands().put(
        "loop",
        customShorthand("loop", "react.shorthands.custom", false)
    );
    Map<String, Command> knownCommands = new LinkedHashMap<>();
    CommandMap commandMap = commandMap(knownCommands);
    Mockito.when(commandMap.dispatch(Mockito.any(), Mockito.eq("loop"))).thenAnswer(invocation -> {
      Player sender = invocation.getArgument(0);
      return knownCommands.get("loop").execute(sender, "loop", new String[0]);
    });
    ShorthandCommandService service = new ShorthandCommandService(tweak, commandMap, itemCatalog());
    Player player = permittedPlayer();

    try (MockedStatic<Bukkit> bukkit = mockBukkitWithoutPlayers()) {
      service.register();
      knownCommands.get("loop").execute(player, "loop", new String[0]);

      Mockito.verify(commandMap).dispatch(player, "loop");
      Assertions.assertEquals("Stopped recursive shorthand /loop.", singleFeedback(player));
    }
  }

  @Test
  public void individualSwitchesControlRegistrationWithoutTouchingReload() throws Exception {
    TweakShorthands tweak = new TweakShorthands();
    enableOnly(tweak, "gms", "gmc", "more", "rl");
    Map<String, Command> knownCommands = new LinkedHashMap<>();
    Command vanillaGive = Mockito.mock(Command.class);
    Command serverReload = Mockito.mock(Command.class);
    knownCommands.put("give", vanillaGive);
    knownCommands.put("reload", serverReload);
    CommandMap commandMap = commandMap(knownCommands);
    ShorthandCommandService service = new ShorthandCommandService(tweak, commandMap, itemCatalog());

    try (MockedStatic<Bukkit> bukkit = mockBukkitWithoutPlayers()) {
      service.register();

      Assertions.assertNotNull(knownCommands.get("gms"));
      Assertions.assertNotNull(knownCommands.get("gmc"));
      Assertions.assertNotNull(knownCommands.get("more"));
      Assertions.assertNotNull(knownCommands.get("rl"));
      Assertions.assertNull(knownCommands.get("gmsp"));
      Assertions.assertSame(vanillaGive, knownCommands.get("give"));
      Assertions.assertSame(serverReload, knownCommands.get("reload"));
      Assertions.assertFalse(knownCommands.containsKey("reload-confirm"));

      service.unregister();

      Assertions.assertNull(knownCommands.get("gms"));
      Assertions.assertNull(knownCommands.get("gmc"));
      Assertions.assertNull(knownCommands.get("more"));
      Assertions.assertNull(knownCommands.get("rl"));
      Assertions.assertSame(vanillaGive, knownCommands.get("give"));
      Assertions.assertSame(serverReload, knownCommands.get("reload"));
    }
  }

  @Test
  public void giveUsesRegistryItemCompletionAndRestoresThePreviousCommand() throws Exception {
    TweakShorthands tweak = new TweakShorthands();
    enableOnly(tweak, "give");
    ItemStack fullStack = Mockito.mock(ItemStack.class);
    ItemStack finalItem = Mockito.mock(ItemStack.class);
    Map<Integer, ItemStack> createdStacks = new HashMap<>();
    createdStacks.put(64, fullStack);
    createdStacks.put(1, finalItem);
    ShorthandCommandService.CatalogItem stone = item("stone", 64, createdStacks);
    Map<String, Command> knownCommands = new LinkedHashMap<>();
    Command vanillaGive = Mockito.mock(Command.class);
    knownCommands.put("give", vanillaGive);
    knownCommands.put("minecraft:give", vanillaGive);
    CommandMap commandMap = commandMap(knownCommands);
    ShorthandCommandService service = new ShorthandCommandService(tweak, commandMap, itemCatalog(stone));
    Player player = permittedPlayer();
    PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
    World world = Mockito.mock(World.class);
    Mockito.when(player.getInventory()).thenReturn(inventory);
    Mockito.when(player.getWorld()).thenReturn(world);
    Mockito.when(world.getFeatureFlags()).thenReturn(Set.of());
    Mockito.when(inventory.addItem(Mockito.any(ItemStack[].class))).thenReturn(new HashMap<>());

    try (MockedStatic<Bukkit> bukkit = mockBukkitWithoutPlayers()) {
      service.register();
      Command give = knownCommands.get("give");

      Assertions.assertNotSame(vanillaGive, give);
      Assertions.assertSame(vanillaGive, knownCommands.get("minecraft:give"));
      Assertions.assertTrue(give.tabComplete(player, "give", new String[]{"sto"}).contains("minecraft:stone"));

      give.execute(player, "give", new String[]{"minecraft:stone", "65"});

      ArgumentCaptor<ItemStack[]> stacks = ArgumentCaptor.forClass(ItemStack[].class);
      Mockito.verify(inventory).addItem(stacks.capture());
      Assertions.assertArrayEquals(new ItemStack[]{fullStack, finalItem}, stacks.getValue());
      Assertions.assertEquals("Gave 65 minecraft:stone.", singleFeedback(player));

      service.unregister();
      Assertions.assertSame(vanillaGive, knownCommands.get("give"));
      Assertions.assertFalse(knownCommands.containsKey("react:give"));
    }
  }

  @Test
  public void giveRejectsNonPositiveAndExcessiveAmounts() throws Exception {
    TweakShorthands tweak = new TweakShorthands();
    enableOnly(tweak, "give");
    ShorthandCommandService.CatalogItem stone = item("stone", 64, Map.of());
    Map<String, Command> knownCommands = new LinkedHashMap<>();
    CommandMap commandMap = commandMap(knownCommands);
    ShorthandCommandService service = new ShorthandCommandService(tweak, commandMap, itemCatalog(stone));
    Player player = permittedPlayer();
    PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
    World world = Mockito.mock(World.class);
    Mockito.when(player.getInventory()).thenReturn(inventory);
    Mockito.when(player.getWorld()).thenReturn(world);
    Mockito.when(world.getFeatureFlags()).thenReturn(Set.of());

    try (MockedStatic<Bukkit> bukkit = mockBukkitWithoutPlayers()) {
      service.register();
      Command give = knownCommands.get("give");

      give.execute(player, "give", new String[]{"minecraft:stone", "0"});
      give.execute(player, "give", new String[]{"minecraft:stone", "-1"});
      give.execute(player, "give", new String[]{"minecraft:stone", "6401"});

      Mockito.verify(inventory, Mockito.never()).addItem(Mockito.any(ItemStack[].class));
      ArgumentCaptor<Component> feedback = ArgumentCaptor.forClass(Component.class);
      Mockito.verify(player, Mockito.times(3)).sendMessage(feedback.capture());
      for (Component message : feedback.getAllValues()) {
        Assertions.assertEquals("Amount must be between 1 and 6400.", PLAIN.serialize(message));
      }
    }
  }

  @Test
  public void giveRendersUsageAndUnavailableItemFeedback() throws Exception {
    TweakShorthands tweak = new TweakShorthands();
    enableOnly(tweak, "give");
    Map<String, Command> knownCommands = new LinkedHashMap<>();
    ShorthandCommandService service = new ShorthandCommandService(tweak, commandMap(knownCommands), itemCatalog());
    Player player = permittedPlayer();

    try (MockedStatic<Bukkit> bukkit = mockBukkitWithoutPlayers()) {
      service.register();
      Command give = knownCommands.get("give");

      give.execute(player, "give", new String[0]);
      give.execute(player, "give", new String[]{"minecraft:missing", "1"});

      ArgumentCaptor<Component> feedback = ArgumentCaptor.forClass(Component.class);
      Mockito.verify(player, Mockito.times(2)).sendMessage(feedback.capture());
      Assertions.assertEquals("Usage: /give <item> <amount>", PLAIN.serialize(feedback.getAllValues().get(0)));
      Assertions.assertEquals("Unknown or unavailable item: minecraft:missing", PLAIN.serialize(feedback.getAllValues().get(1)));
    }
  }

  @Test
  public void moreUsesAnExactMaximumSizeCopyOfTheHeldItem() throws Exception {
    TweakShorthands tweak = new TweakShorthands();
    enableOnly(tweak, "more");
    Map<String, Command> knownCommands = new LinkedHashMap<>();
    CommandMap commandMap = commandMap(knownCommands);
    ShorthandCommandService service = new ShorthandCommandService(tweak, commandMap, itemCatalog());
    Player player = permittedPlayer();
    PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
    ItemStack held = Mockito.mock(ItemStack.class);
    ItemStack exactCopy = Mockito.mock(ItemStack.class);
    Mockito.when(player.getInventory()).thenReturn(inventory);
    Mockito.when(inventory.getItemInMainHand()).thenReturn(held);
    Mockito.when(held.isEmpty()).thenReturn(false);
    Mockito.when(held.getMaxStackSize()).thenReturn(16);
    Mockito.when(held.asQuantity(16)).thenReturn(exactCopy);
    Mockito.when(inventory.addItem(Mockito.any(ItemStack[].class))).thenReturn(new HashMap<>());

    try (MockedStatic<Bukkit> bukkit = mockBukkitWithoutPlayers()) {
      service.register();
      knownCommands.get("more").execute(player, "more", new String[0]);

      Mockito.verify(held).asQuantity(16);
      ArgumentCaptor<ItemStack[]> stacks = ArgumentCaptor.forClass(ItemStack[].class);
      Mockito.verify(inventory).addItem(stacks.capture());
      Assertions.assertArrayEquals(new ItemStack[]{exactCopy}, stacks.getValue());
      Assertions.assertEquals("Gave one exact stack of the held item.", singleFeedback(player));
    }
  }

  @Test
  public void gameModeCommandsOnlyChangeTheCaster() throws Exception {
    TweakShorthands tweak = new TweakShorthands();
    enableOnly(tweak, "gms", "gmsp", "gmc");
    Map<String, Command> knownCommands = new LinkedHashMap<>();
    CommandMap commandMap = commandMap(knownCommands);
    ShorthandCommandService service = new ShorthandCommandService(tweak, commandMap, itemCatalog());
    Player player = permittedPlayer();

    try (MockedStatic<Bukkit> bukkit = mockBukkitWithoutPlayers()) {
      service.register();
      knownCommands.get("gms").execute(player, "gms", new String[0]);
      knownCommands.get("gmsp").execute(player, "gmsp", new String[0]);
      knownCommands.get("gmc").execute(player, "gmc", new String[0]);

      Mockito.verify(player).setGameMode(GameMode.SURVIVAL);
      Mockito.verify(player).setGameMode(GameMode.SPECTATOR);
      Mockito.verify(player).setGameMode(GameMode.CREATIVE);
      ArgumentCaptor<Component> feedback = ArgumentCaptor.forClass(Component.class);
      Mockito.verify(player, Mockito.times(3)).sendMessage(feedback.capture());
      Assertions.assertEquals(
          List.of(
              "Game mode set to survival.",
              "Game mode set to spectator.",
              "Game mode set to creative."
          ),
          feedback.getAllValues().stream().map(PLAIN::serialize).toList()
      );
    }
  }

  @Test
  public void rlDispatchesBareReloadWithoutConfirm() throws Exception {
    TweakShorthands tweak = new TweakShorthands();
    enableOnly(tweak, "rl");
    Map<String, Command> knownCommands = new LinkedHashMap<>();
    CommandMap commandMap = commandMap(knownCommands);
    ShorthandCommandService service = new ShorthandCommandService(tweak, commandMap, itemCatalog());
    Player player = permittedPlayer();

    try (MockedStatic<Bukkit> bukkit = mockBukkitWithoutPlayers()) {
      bukkit.when(() -> Bukkit.dispatchCommand(player, "reload")).thenReturn(false);
      service.register();
      knownCommands.get("rl").execute(player, "rl", new String[0]);

      bukkit.verify(() -> Bukkit.dispatchCommand(player, "reload"));
      bukkit.verify(() -> Bukkit.dispatchCommand(player, "reload confirm"), Mockito.never());
      Assertions.assertEquals("The server's /reload command is unavailable.", singleFeedback(player));
    }
  }

  private static String singleFeedback(Player player) {
    ArgumentCaptor<Component> feedback = ArgumentCaptor.forClass(Component.class);
    Mockito.verify(player).sendMessage(feedback.capture());
    return PLAIN.serialize(feedback.getValue());
  }

  private static Player permittedPlayer() {
    Player player = Mockito.mock(Player.class);
    Mockito.when(player.hasPermission(Mockito.anyString())).thenReturn(true);
    return player;
  }

  private static ShorthandCommandService.CatalogItem item(String path, int maxStackSize, Map<Integer, ItemStack> stacks) {
    return new ShorthandCommandService.CatalogItem(
        NamespacedKey.minecraft(path).asString(),
        maxStackSize,
        Set.of(),
        stacks::get
    );
  }

  private static ShorthandCommandService.ItemCatalog itemCatalog(ShorthandCommandService.CatalogItem... items) {
    return ShorthandCommandService.ItemCatalog.of(items);
  }

  private static CommandMap commandMap(Map<String, Command> knownCommands) {
    CommandMap commandMap = Mockito.mock(CommandMap.class);
    Mockito.when(commandMap.getKnownCommands()).thenReturn(knownCommands);
    Mockito.when(commandMap.register(Mockito.anyString(), Mockito.eq("react"), Mockito.any(Command.class)))
        .thenAnswer(invocation -> {
          String label = invocation.getArgument(0);
          Command command = invocation.getArgument(2);
          knownCommands.put("react:" + label, command);
          knownCommands.put(label, command);
          command.register(commandMap);
          return true;
        });
    return commandMap;
  }

  private static MockedStatic<Bukkit> mockBukkitWithoutPlayers() {
    MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
    bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
    return bukkit;
  }

  private static TweakShorthands.CustomShorthand customShorthand(
      String command,
      String permission,
      boolean overrideExisting
  ) throws Exception {
    TweakShorthands.CustomShorthand definition = new TweakShorthands.CustomShorthand();
    setField(definition, "command", command);
    setField(definition, "permission", permission);
    setField(definition, "overrideExisting", overrideExisting);
    return definition;
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static void enableOnly(TweakShorthands tweak, String... enabledFields) throws Exception {
    Set<String> enabled = Set.of(enabledFields);
    for (String fieldName : List.of("gms", "gmsp", "gmc", "give", "more", "rl")) {
      Field field = TweakShorthands.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.setBoolean(tweak, enabled.contains(fieldName));
    }
  }
}
