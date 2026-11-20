package art.arcane.react.core.gui;

import art.arcane.react.api.monitor.configuration.MonitorConfiguration;
import art.arcane.react.api.monitor.configuration.MonitorGroup;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.platform.SettingsAction;
import art.arcane.react.platform.SettingsClick;
import art.arcane.react.platform.SettingsScreen;
import art.arcane.react.platform.SettingsScreenHost;
import art.arcane.react.platform.SettingsTile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorConfigMenuTest {
  private static final UUID VIEWER = UUID.randomUUID();

  private final Map<String, Sampler> registered = new LinkedHashMap<>();
  private final RecordingHost host = new RecordingHost();
  private final List<Runnable> delayed = new ArrayList<>();
  private final ViewerTasks tasks = (viewerId, task, delayTicks) -> {
    if (delayTicks > 0) {
      delayed.add(task);
    } else {
      task.run();
    }
  };
  private final MonitorConfigMenu menu = new MonitorConfigMenu(host, (sampler) -> !sampler.getId().startsWith("hidden-"),
      this::all, registered::get, tasks, new Random(7));
  private final List<MonitorConfiguration> saves = new ArrayList<>();
  private final MonitorConfiguration configuration = MonitorConfiguration.builder().groups(new ArrayList<>(List.of(
      MonitorGroup.builder().name("Server").color("#ff0000").sampler("tick-time").sampler("memory-used").head("memory-used").build(),
      MonitorGroup.builder().name("Empty").color("#00ff00").build()
  ))).build();

  MonitorConfigMenuTest() {
    register("tick-time", 50);
    register("memory-used", 1024);
    register("adapt-xp", 3);
    register("hidden-thing", 1);
    register("unknown", 0);
  }

  @Test
  void groupListShowsEveryGroupAsDyedTileAndCreateButton() {
    SettingsScreen screen = menu.groupsScreen(configuration);

    assertEquals(ReactLanguage.plain(GuiMessages.MONITOR_TITLE), screen.title());
    assertEquals(3, screen.rows());
    assertEquals(9, screen.columns());
    SettingsTile server = tile(screen, "group-Server");
    assertEquals(-4, server.position());
    assertEquals(0, server.row());
    assertEquals("leather_chestplate", server.icon());
    assertEquals(0xFF0000, server.dyeColor());
    assertEquals("Server", server.name());
    assertEquals(List.of("[memory-used]", "tick-time", "memory-used"), server.lore());
    assertEquals(new SettingsAction.Custom(MonitorConfigMenu.EDIT_GROUP, "0"), server.actionFor(SettingsClick.LEFT));
    assertEquals(-3, tile(screen, "group-Empty").position());
    assertEquals(List.of("[unknown]"), tile(screen, "group-Empty").lore());

    SettingsTile create = tile(screen, "creategroup");
    assertEquals(0, create.position());
    assertEquals(2, create.row());
    assertEquals("emerald", create.icon());
    assertEquals(new SettingsAction.Custom(MonitorConfigMenu.CREATE_GROUP, ""), create.actionFor(SettingsClick.LEFT));
  }

  @Test
  void groupEditorListsSamplersAndControls() {
    SettingsScreen screen = menu.groupScreen(configuration.getGroups().get(0));

    assertEquals(ReactLanguage.plain(GuiMessages.MONITOR_GROUP_TITLE, art.arcane.volmlib.util.localization.MessageArgument.untrusted("group", "Server")),
        screen.title());
    SettingsTile tick = tile(screen, "sample-tick-time");
    SettingsTile memory = tile(screen, "sample-memory-used");
    assertFalse(tick.enchanted());
    assertTrue(memory.enchanted());
    assertEquals(-3, memory.position());
    assertEquals(4, tick.lore().size());
    assertEquals(ReactLanguage.plain(GuiMessages.SAMPLER_REMOVE), tick.lore().get(3));
    assertEquals(new SettingsAction.Custom(MonitorConfigMenu.SET_HEAD, "tick-time"), tick.actionFor(SettingsClick.LEFT));
    assertEquals(new SettingsAction.Custom(MonitorConfigMenu.REMOVE_SAMPLER, "tick-time"), tick.actionFor(SettingsClick.SHIFT_RIGHT));
    assertNull(tick.actionFor(SettingsClick.RIGHT));

    assertEquals("emerald", tile(screen, "addnew").icon());
    assertEquals(-1, tile(screen, "renamegroup").position());
    SettingsTile recolor = tile(screen, "recolorgroup");
    assertEquals(-2, recolor.position());
    assertEquals(0xFF0000, recolor.dyeColor());
    SettingsTile delete = tile(screen, "deletegroup");
    assertEquals(1, delete.position());
    assertNull(delete.actionFor(SettingsClick.LEFT));
    assertEquals(new SettingsAction.Custom(MonitorConfigMenu.DELETE_GROUP, ""), delete.actionFor(SettingsClick.SHIFT_LEFT));
  }

  @Test
  void editingHeadAndRemovingSamplersSavesAndRefreshes() {
    menu.open(VIEWER, configuration, saves::add);
    host.click("group-Server", SettingsClick.LEFT);
    host.click("sample-tick-time", SettingsClick.LEFT);

    MonitorGroup server = configuration.getGroups().get(0);
    assertEquals("tick-time", server.getHead());
    assertEquals(1, saves.size());
    assertTrue(tile(host.screen, "sample-tick-time").enchanted());

    host.click("sample-tick-time", SettingsClick.SHIFT_RIGHT);
    assertEquals(List.of("memory-used"), server.getSamplers());
    assertEquals("memory-used", server.getHeadOrSomething());
    assertEquals(2, saves.size());
  }

  @Test
  void closingTheGroupEditorSavesAndReturnsToTheList() {
    menu.open(VIEWER, configuration, saves::add);
    host.click("group-Server", SettingsClick.LEFT);
    host.userClose();

    assertEquals(1, saves.size());
    assertEquals(ReactLanguage.plain(GuiMessages.MONITOR_TITLE), host.screen.title());

    host.userClose();
    assertEquals(2, saves.size());
  }

  @Test
  void deletingAGroupNeedsShiftLeftAndReturnsToTheList() {
    menu.open(VIEWER, configuration, saves::add);
    host.click("group-Empty", SettingsClick.LEFT);
    host.click("deletegroup", SettingsClick.LEFT);
    assertEquals(2, configuration.getGroups().size());

    host.click("deletegroup", SettingsClick.SHIFT_LEFT);
    assertEquals(1, configuration.getGroups().size());
    assertEquals(ReactLanguage.plain(GuiMessages.MONITOR_TITLE), host.screen.title());
    assertEquals(1, saves.size());
  }

  @Test
  void samplerPickerOffersListedSamplersByGroupAndAddsThePick() {
    menu.open(VIEWER, configuration, saves::add);
    host.click("group-Empty", SettingsClick.LEFT);
    host.click("addnew", SettingsClick.LEFT);

    SettingsScreen hub = host.screen;
    assertEquals(ReactLanguage.plain(GuiMessages.SAMPLER_TITLE), hub.title());
    assertEquals("spyglass", tile(hub, "hub-all").icon());
    assertTrue(tile(hub, "hub-all").lore().contains(ReactLanguage.plain(GuiMessages.SAMPLER_HUB_GROUP_COUNT,
        art.arcane.volmlib.util.localization.MessageArgument.untrusted("count", 3))));
    SettingsTile adapt = tile(hub, "hub-group-" + ReactGuiTaxonomy.groupOrder("adapt-xp"));
    assertEquals(-3, adapt.position());

    host.click(adapt.key(), SettingsClick.LEFT);
    assertNotNull(find(host.screen, "sample-adapt-xp"));
    assertNull(find(host.screen, "sample-tick-time"));
    assertNotNull(find(host.screen, "hub-back"));

    host.click("sample-adapt-xp", SettingsClick.LEFT);
    assertEquals(List.of("adapt-xp"), configuration.getGroups().get(1).getSamplers());
    assertEquals(1, saves.size());
    assertNotNull(find(host.screen, "sample-adapt-xp"));
  }

  @Test
  void samplerPickerPagesEighteenAtATime() {
    for (int index = 0; index < 20; index++) {
      register("extra-" + index, index);
    }
    SettingsScreen first = menu.samplerGroupScreen(List.of(), null, 0);
    assertNull(find(first, "page-back"));
    assertEquals(new SettingsAction.Custom(MonitorConfigMenu.PICKER_PAGE, "1"), tile(first, "page-forward").actionFor(SettingsClick.LEFT));
    assertEquals(4, tile(first, "page-forward").position());

    SettingsScreen second = menu.samplerGroupScreen(List.of(), null, 1);
    assertEquals(-4, tile(second, "page-back").position());
    assertNull(find(second, "page-forward"));

    SettingsScreen outOfRange = menu.samplerGroupScreen(List.of(), null, 9);
    assertNull(find(outOfRange, "page-back"));
  }

  @Test
  void presetColorPickAppliesAndEscapeKeepsTheColor() {
    menu.open(VIEWER, configuration, saves::add);
    host.click("group-Server", SettingsClick.LEFT);
    host.click("recolorgroup", SettingsClick.LEFT);

    SettingsScreen colors = host.screen;
    assertEquals(19, colors.tiles().size());
    SettingsTile first = colors.tiles().get(0);
    assertNull(first.name());
    assertEquals(0x48F06F, first.dyeColor());
    host.click(first.key(), SettingsClick.LEFT);
    assertEquals("#48f06f", configuration.getGroups().get(0).getColor());
    assertEquals(1, saves.size());

    host.click("recolorgroup", SettingsClick.LEFT);
    host.userClose();
    assertEquals("#48f06f", configuration.getGroups().get(0).getColor());
    assertNotNull(find(host.screen, "recolorgroup"));
  }

  @Test
  void customColorUsesADropperAndEscapeAppliesTheAdjustedColor() {
    menu.open(VIEWER, configuration, saves::add);
    host.click("group-Server", SettingsClick.LEFT);
    host.click("recolorgroup", SettingsClick.LEFT);
    host.click("custom", SettingsClick.LEFT);

    SettingsScreen custom = host.screen;
    assertTrue(custom.dropper());
    assertEquals(0xFF0000, tile(custom, "buf").dyeColor());
    assertEquals(4, tile(custom, "buf").slot(3));
    assertEquals(MonitorConfigMenu.adjust(0xFF0000, "darken", 15), tile(custom, "darken").dyeColor());
    assertEquals(new SettingsAction.Custom(MonitorConfigMenu.ADJUST_COLOR, "spin:-15"), tile(custom, "spinleft").actionFor(SettingsClick.SHIFT_LEFT));

    host.click("darken", SettingsClick.LEFT);
    int darker = MonitorConfigMenu.adjust(0xFF0000, "darken", 5);
    assertEquals(darker, tile(host.screen, "buf").dyeColor());
    host.userClose();
    assertEquals(MonitorConfigMenu.hex(darker), configuration.getGroups().get(0).getColor());
  }

  @Test
  void hexPromptKeepsTheWindowAndReopensThePicker() {
    menu.open(VIEWER, configuration, saves::add);
    host.click("group-Server", SettingsClick.LEFT);
    host.click("recolorgroup", SettingsClick.LEFT);
    host.click("custom", SettingsClick.LEFT);
    host.click("hexcode", SettingsClick.LEFT);

    assertEquals(0, host.closes);
    host.userClose();
    assertEquals("#ff0000", configuration.getGroups().get(0).getColor());
    assertTrue(menu.prompts().onChat(VIEWER, "nonsense"));
    assertEquals(0xFF0000, tile(host.screen, "buf").dyeColor());

    host.click("hexcode", SettingsClick.LEFT);
    assertTrue(menu.prompts().onChat(VIEWER, "123abc"));
    assertEquals(0x123ABC, tile(host.screen, "buf").dyeColor());
    host.click("buf", SettingsClick.LEFT);
    assertEquals("#123abc", configuration.getGroups().get(0).getColor());
  }

  @Test
  void createAndRenamePromptsUseTheNextChatMessage() {
    menu.open(VIEWER, configuration, saves::add);
    host.click("creategroup", SettingsClick.LEFT);
    assertEquals(1, host.closes);
    assertEquals(ReactLanguage.plain(GuiMessages.MONITOR_TITLE), host.screen.title());

    assertTrue(menu.prompts().onChat(VIEWER, "Lag"));
    assertFalse(menu.prompts().onChat(VIEWER, "hello"));
    assertEquals(3, configuration.getGroups().size());
    MonitorGroup created = configuration.getGroups().get(2);
    assertEquals("Lag", created.getName());
    assertTrue(MonitorConfigMenu.PRESET_COLORS.contains(created.getColorValue() & 0xFFFFFF));
    assertNotNull(find(host.screen, "renamegroup"));

    host.click("renamegroup", SettingsClick.LEFT);
    assertTrue(menu.prompts().onChat(VIEWER, "Lag Spikes"));
    assertEquals("Lag Spikes", created.getName());
    assertEquals(2, saves.size());
  }

  @Test
  void createPromptTimeoutReturnsToTheList() {
    menu.open(VIEWER, configuration, saves::add);
    host.click("creategroup", SettingsClick.LEFT);
    int opens = host.opens;

    delayed.forEach(Runnable::run);
    assertFalse(menu.prompts().pending(VIEWER));
    assertEquals(opens + 1, host.opens);
    assertEquals(ReactLanguage.plain(GuiMessages.MONITOR_TITLE), host.screen.title());
    assertEquals(2, configuration.getGroups().size());
  }

  @Test
  void hexParsingAcceptsWithAndWithoutHash() {
    assertEquals(0xABCDEF, MonitorConfigMenu.parseHex("#abcdef"));
    assertEquals(0xABCDEF, MonitorConfigMenu.parseHex(" abcdef "));
    assertNull(MonitorConfigMenu.parseHex("zz"));
    assertEquals("#0000ff", MonitorConfigMenu.hex(0x0000FF));
  }

  private Collection<Sampler> all() {
    return registered.values();
  }

  private void register(String id, double value) {
    registered.put(id, new FixedSampler(id, value));
  }

  private static SettingsTile tile(SettingsScreen screen, String key) {
    SettingsTile tile = find(screen, key);
    assertNotNull(tile, "no tile " + key);
    return tile;
  }

  private static SettingsTile find(SettingsScreen screen, String key) {
    for (SettingsTile tile : screen.tiles()) {
      if (Objects.equals(tile.key(), key)) {
        return tile;
      }
    }
    return null;
  }

  private static final class RecordingHost implements SettingsScreenHost {
    private SettingsScreen screen;
    private ClickHandler handler;
    private int opens;
    private int closes;

    @Override
    public void open(UUID viewerId, SettingsScreen screen, ClickHandler handler) {
      ClickHandler previous = this.handler;
      boolean replaced = this.screen != null && (!this.screen.title().equals(screen.title()) || this.screen.columns() != screen.columns());
      this.screen = screen;
      this.handler = handler;
      opens++;
      if (replaced && previous != null) {
        previous.onClosed(viewerId);
      }
    }

    @Override
    public void close(UUID viewerId) {
      closes++;
      if (handler != null) {
        handler.onClosed(viewerId);
      }
    }

    @Override
    public void playPageTurn(UUID viewerId) {
    }

    void click(String key, SettingsClick click) {
      SettingsTile tile = MonitorConfigMenuTest.tile(screen, key);
      SettingsAction action = tile.actionFor(click);
      if (action != null) {
        handler.onClick(VIEWER, tile, click, action);
      }
    }

    void userClose() {
      handler.onClosed(VIEWER);
    }
  }

  private record FixedSampler(String id, double value) implements Sampler {
    @Override
    public String getId() {
      return id;
    }

    @Override
    public double sample() {
      return value;
    }

    @Override
    public String formattedValue(double t) {
      return Double.toString(t);
    }

    @Override
    public String formattedSuffix(double t) {
      return "";
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
  }
}
