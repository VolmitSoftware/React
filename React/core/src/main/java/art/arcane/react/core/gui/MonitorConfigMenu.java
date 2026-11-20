package art.arcane.react.core.gui;

import art.arcane.react.api.monitor.configuration.MonitorConfiguration;
import art.arcane.react.api.monitor.configuration.MonitorGroup;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.content.sampler.SamplerUnknown;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.platform.SettingsAction;
import art.arcane.react.platform.SettingsClick;
import art.arcane.react.platform.SettingsScreen;
import art.arcane.react.platform.SettingsScreenHost;
import art.arcane.react.platform.SettingsTile;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The per-player monitor editor behind {@code /react config monitor}: the group list, the group editor, the sampler
 * picker and the colour picker. Group names and hex colours are asked for in chat through {@link #prompts()}.
 */
public final class MonitorConfigMenu {
  public static final List<Integer> PRESET_COLORS = List.of(
      0x48F06F, 0xF0C842, 0x4158F0, 0xF04857, 0xBDF041, 0xF08839, 0x3ADBF0, 0xBF41F0, 0x48F06F,
      0x8049F0, 0x3AF0BD, 0xF0633A, 0xF0E641, 0xF03AD2, 0x48A0F0, 0x7EF05A, 0xF0AD41, 0xF05F8D
  );

  static final String EDIT_GROUP = "edit-group";
  static final String CREATE_GROUP = "create-group";
  static final String SET_HEAD = "set-head";
  static final String REMOVE_SAMPLER = "remove-sampler";
  static final String ADD_SAMPLER = "add-sampler";
  static final String RENAME_GROUP = "rename-group";
  static final String RECOLOR_GROUP = "recolor-group";
  static final String DELETE_GROUP = "delete-group";
  static final String PICKER_ALL = "picker-all";
  static final String PICKER_GROUP = "picker-group";
  static final String PICKER_PAGE = "picker-page";
  static final String PICKER_HUB = "picker-hub";
  static final String PICK_SAMPLER = "pick-sampler";
  static final String PICK_COLOR = "pick-color";
  static final String CUSTOM_COLOR = "custom-color";
  static final String USE_COLOR = "use-color";
  static final String ADJUST_COLOR = "adjust-color";
  static final String ENTER_HEX = "enter-hex";

  private static final String BACKGROUND = "black_stained_glass_pane";
  private static final String FALLBACK_ICON = "chest";
  private static final String COLOR_ICON = "leather_chestplate";
  private static final int ROWS = 3;
  private static final int PICKER_PAGE_SIZE = 9 * 2;
  private static final int CUSTOM_START_COLOR = 0xBB6666;

  private final SettingsScreenHost host;
  private final ChatPrompts prompts;
  private final Predicate<Sampler> listed;
  private final Supplier<Collection<Sampler>> samplers;
  private final Function<String, Sampler> lookup;
  private final ViewerTasks tasks;
  private final Random random;
  private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

  /**
   * {@code listed} decides which registered samplers the sampler picker offers.
   */
  public MonitorConfigMenu(SettingsScreenHost host, Predicate<Sampler> listed) {
    this(host, listed, MonitorConfigMenu::registeredSamplers, SamplerHosts::sampler, ViewerTasks.OWNER_THREAD, new Random());
  }

  MonitorConfigMenu(SettingsScreenHost host, Predicate<Sampler> listed, Supplier<Collection<Sampler>> samplers,
                    Function<String, Sampler> lookup, ViewerTasks tasks, Random random) {
    this.host = host;
    this.prompts = new ChatPrompts(tasks);
    this.listed = listed;
    this.samplers = samplers;
    this.lookup = lookup;
    this.tasks = tasks;
    this.random = random;
  }

  public ChatPrompts prompts() {
    return prompts;
  }

  /**
   * Opens the group list. {@code saver} persists the configuration after every change.
   */
  public void open(UUID viewerId, MonitorConfiguration configuration, Consumer<MonitorConfiguration> saver) {
    if (viewerId == null || configuration == null || saver == null) {
      return;
    }
    configuration.setGroups(configuration.getGroups() == null ? new ArrayList<>() : new ArrayList<>(configuration.getGroups()));
    Session session = new Session(configuration, saver);
    prompts.forget(viewerId);
    sessions.put(viewerId, session);
    if (J.isPrimaryThread()) {
      showGroups(viewerId, session);
    } else {
      tasks.run(viewerId, () -> showGroups(viewerId, session), 0);
    }
  }

  public void forget(UUID viewerId) {
    if (viewerId != null) {
      sessions.remove(viewerId);
      prompts.forget(viewerId);
    }
  }

  public void clear() {
    sessions.clear();
    prompts.clear();
  }

  SettingsScreen groupsScreen(MonitorConfiguration configuration) {
    List<SettingsTile> tiles = new ArrayList<>();
    int index = 0;
    for (MonitorGroup group : configuration.getGroups()) {
      List<String> lore = new ArrayList<>();
      lore.add("[" + group.getHeadOrSomething() + "]");
      lore.addAll(group.getSamplers());
      tiles.add(new SettingsTile("group-" + group.getName(), position(index), row(index), COLOR_ICON, FALLBACK_ICON, group.getName(),
          lore, false, Map.of(SettingsClick.LEFT, action(EDIT_GROUP, Integer.toString(index))), rgb(group.getColorValue())));
      index++;
    }
    tiles.add(tile("creategroup", 0, 2, "emerald", plain(GuiMessages.GROUP_CREATE), List.of(plain(GuiMessages.GROUP_CREATE_LORE)),
        Map.of(SettingsClick.LEFT, action(CREATE_GROUP, ""))));
    return new SettingsScreen(plain(GuiMessages.MONITOR_TITLE), ROWS, BACKGROUND, tiles);
  }

  SettingsScreen groupScreen(MonitorGroup group) {
    List<SettingsTile> tiles = new ArrayList<>();
    String head = group.getHeadOrSomething();
    int index = 0;
    for (String samplerId : group.getSamplers()) {
      int slot = index++;
      Sampler sampler = lookup.apply(samplerId);
      if (sampler == null) {
        continue;
      }
      Map<SettingsClick, SettingsAction> actions = new EnumMap<>(SettingsClick.class);
      actions.put(SettingsClick.LEFT, action(SET_HEAD, sampler.getId()));
      actions.put(SettingsClick.SHIFT_RIGHT, action(REMOVE_SAMPLER, sampler.getId()));
      tiles.add(new SettingsTile("sample-" + sampler.getId(), position(slot), row(slot), ReactGuiTaxonomy.iconForId(sampler.getId()), FALLBACK_ICON,
          sampler.getName(), List.of(
              plain(GuiMessages.SAMPLER_GROUP, MessageArgument.untrusted("group", ReactGuiTaxonomy.groupLabel(sampler.getId()))),
              sampler.format(sampler.sample()),
              plain(GuiMessages.SAMPLER_SET_HEADER),
              plain(GuiMessages.SAMPLER_REMOVE)
          ), head.equals(samplerId), actions, null));
    }
    tiles.add(tile("addnew", 0, 2, "emerald", plain(GuiMessages.SAMPLER_ADD), List.of(plain(GuiMessages.SAMPLER_ADD_LORE)),
        Map.of(SettingsClick.LEFT, action(ADD_SAMPLER, ""))));
    tiles.add(tile("renamegroup", -1, 2, "writable_book", plain(GuiMessages.GROUP_RENAME), List.of(plain(GuiMessages.GROUP_RENAME_LORE)),
        Map.of(SettingsClick.LEFT, action(RENAME_GROUP, ""))));
    tiles.add(colorTile("recolorgroup", -2, 2, rgb(group.getColorValue()), plain(GuiMessages.GROUP_COLOR), List.of(plain(GuiMessages.GROUP_COLOR_LORE)),
        Map.of(SettingsClick.LEFT, action(RECOLOR_GROUP, ""))));
    tiles.add(tile("deletegroup", 1, 2, "barrier", plain(GuiMessages.GROUP_DELETE), List.of(plain(GuiMessages.GROUP_DELETE_LORE)),
        Map.of(SettingsClick.SHIFT_LEFT, action(DELETE_GROUP, ""))));
    return new SettingsScreen(plain(GuiMessages.MONITOR_GROUP_TITLE, MessageArgument.untrusted("group", group.getName())), ROWS, BACKGROUND, tiles);
  }

  SettingsScreen samplerHubScreen(List<String> without) {
    List<Sampler> listedSamplers = listedSamplers(without);
    Map<Integer, ReactGuiTaxonomy.EntryGroup> groups = new TreeMap<>();
    Map<Integer, Integer> counts = new TreeMap<>();
    for (Sampler sampler : listedSamplers) {
      ReactGuiTaxonomy.EntryGroup group = ReactGuiTaxonomy.groupForId(sampler.getId());
      groups.putIfAbsent(group.order(), group);
      counts.merge(group.order(), 1, Integer::sum);
    }

    List<SettingsTile> tiles = new ArrayList<>();
    tiles.add(tile("hub-all", position(0), row(0), "spyglass", plain(GuiMessages.SAMPLER_HUB_ALL), List.of(
        plain(GuiMessages.SAMPLER_HUB_ALL_SUMMARY),
        plain(GuiMessages.SAMPLER_HUB_GROUP_COUNT, MessageArgument.untrusted("count", listedSamplers.size()))
    ), Map.of(SettingsClick.LEFT, action(PICKER_ALL, ""))));
    int index = 1;
    for (ReactGuiTaxonomy.EntryGroup group : groups.values()) {
      int count = counts.get(group.order());
      tiles.add(tile("hub-group-" + group.order(), position(index), row(index), group.icon(), group.label(), List.of(
          plain(GuiMessages.SAMPLER_HUB_GROUP_COUNT, MessageArgument.untrusted("count", count)),
          plain(GuiMessages.SAMPLER_HUB_OPEN_GROUP)
      ), Map.of(SettingsClick.LEFT, action(PICKER_GROUP, Integer.toString(group.order())))));
      index++;
    }
    return new SettingsScreen(plain(GuiMessages.SAMPLER_TITLE), ROWS, BACKGROUND, tiles);
  }

  SettingsScreen samplerGroupScreen(List<String> without, ReactGuiTaxonomy.EntryGroup group, int page) {
    List<Sampler> shown = listedSamplers(without);
    if (group != null) {
      List<Sampler> inGroup = new ArrayList<>();
      for (Sampler sampler : shown) {
        if (ReactGuiTaxonomy.groupOrder(sampler.getId()) == group.order()) {
          inGroup.add(sampler);
        }
      }
      shown = inGroup;
    }

    boolean hasMore = shown.size() > PICKER_PAGE_SIZE * (page + 1);
    int currentPage = Math.max(0, page);
    if (shown.size() <= currentPage * PICKER_PAGE_SIZE) {
      currentPage = 0;
    }
    shown = shown.subList(currentPage * PICKER_PAGE_SIZE, Math.min(shown.size(), (currentPage + 1) * PICKER_PAGE_SIZE));

    List<SettingsTile> tiles = new ArrayList<>();
    int index = 0;
    for (Sampler sampler : shown) {
      tiles.add(tile("sample-" + sampler.getId(), position(index), row(index), ReactGuiTaxonomy.iconForId(sampler.getId()),
          MapMessages.localizedSamplerName(sampler.getId(), sampler.getName()), List.of(
              plain(GuiMessages.SAMPLER_GROUP, MessageArgument.untrusted("group", ReactGuiTaxonomy.groupLabel(sampler.getId()))),
              sampler.format(sampler.sample())
          ), Map.of(SettingsClick.LEFT, action(PICK_SAMPLER, sampler.getId()))));
      index++;
    }
    if (currentPage > 0) {
      tiles.add(tile("page-back", -4, 2, "arrow", plain(GuiMessages.PREVIOUS_PAGE), List.of(),
          Map.of(SettingsClick.LEFT, action(PICKER_PAGE, Integer.toString(currentPage - 1)))));
    }
    if (hasMore) {
      tiles.add(tile("page-forward", 4, 2, "arrow", plain(GuiMessages.NEXT_PAGE), List.of(),
          Map.of(SettingsClick.LEFT, action(PICKER_PAGE, Integer.toString(currentPage + 1)))));
    }
    tiles.add(tile("hub-back", 0, 2, "arrow", plain(GuiMessages.SAMPLER_HUB_BACK), List.of(),
        Map.of(SettingsClick.LEFT, action(PICKER_HUB, ""))));
    String title = group == null
        ? plain(GuiMessages.SAMPLER_TITLE)
        : plain(GuiMessages.SAMPLER_GROUP_TITLE, MessageArgument.untrusted("group", group.label()));
    return new SettingsScreen(title, ROWS, BACKGROUND, tiles);
  }

  SettingsScreen colorsScreen() {
    List<SettingsTile> tiles = new ArrayList<>();
    int index = 0;
    for (int color : PRESET_COLORS) {
      tiles.add(colorTile("color-" + new Color(color).getRGB(), position(index), row(index), color, null, List.of(),
          Map.of(SettingsClick.LEFT, action(PICK_COLOR, Integer.toString(color)))));
      index++;
    }
    tiles.add(tile("custom", 0, 2, "writable_book", plain(GuiMessages.COLOR_CUSTOM), List.of(plain(GuiMessages.CLICK_CUSTOM)),
        Map.of(SettingsClick.LEFT, action(CUSTOM_COLOR, ""))));
    return new SettingsScreen(plain(GuiMessages.COLOR_TITLE), ROWS, BACKGROUND, tiles);
  }

  SettingsScreen customColorScreen(int color) {
    Color current = new Color(color);
    TinyColor tiny = new TinyColor(current);
    String brightness = plain(GuiMessages.COLOR_BRIGHTNESS, MessageArgument.untrusted("percent", Math.round(tiny.getBrightness() * 100)));
    String saturation = plain(GuiMessages.COLOR_SATURATION, MessageArgument.untrusted("percent", Math.round(tiny.getSaturation() * 100)));
    List<SettingsTile> tiles = new ArrayList<>();
    tiles.add(colorTile("buf", 0, 1, color, plain(GuiMessages.COLOR_CURRENT),
        List.of(plain(GuiMessages.CLICK_USE_COLOR), plain(GuiMessages.ESCAPE_USE_COLOR)), Map.of(SettingsClick.LEFT, action(USE_COLOR, ""))));
    tiles.add(adjustTile("darken", -1, 1, color, GuiMessages.COLOR_DARKEN, brightness, GuiMessages.ACTION_DARKEN));
    tiles.add(adjustTile("brighten", 1, 1, color, GuiMessages.COLOR_BRIGHTEN, brightness, GuiMessages.ACTION_BRIGHTEN));
    tiles.add(adjustTile("saturate", 1, 0, color, GuiMessages.COLOR_SATURATE, saturation, GuiMessages.ACTION_SATURATE));
    tiles.add(adjustTile("desaturate", -1, 0, color, GuiMessages.COLOR_DESATURATE, saturation, GuiMessages.ACTION_DESATURATE));
    tiles.add(spinTile("spinleft", -1, 2, color, GuiMessages.COLOR_SPIN_LEFT, GuiMessages.DIRECTION_LEFT, -1));
    tiles.add(spinTile("spinright", 1, 2, color, GuiMessages.COLOR_SPIN_RIGHT, GuiMessages.DIRECTION_RIGHT, 1));
    tiles.add(tile("hexcode", 0, 2, "writable_book", plain(GuiMessages.COLOR_ENTER_HEX), List.of(plain(GuiMessages.CLICK_ENTER_HEX)),
        Map.of(SettingsClick.LEFT, action(ENTER_HEX, ""))));
    return new SettingsScreen(plain(GuiMessages.COLOR_TITLE), ROWS, BACKGROUND, tiles, SettingsScreen.DROPPER_COLUMNS);
  }

  private SettingsTile adjustTile(String kind, int position, int row, int color, TextKey label, String level, TextKey verb) {
    String name = plain(label);
    String action = plain(verb);
    Map<SettingsClick, SettingsAction> actions = new EnumMap<>(SettingsClick.class);
    actions.put(SettingsClick.LEFT, action(ADJUST_COLOR, kind + ":5"));
    actions.put(SettingsClick.SHIFT_LEFT, action(ADJUST_COLOR, kind + ":15"));
    return colorTile(kind, position, row, adjust(color, kind, 15), name, List.of(
        level,
        plain(GuiMessages.CLICK_ADJUST_FIVE, MessageArgument.untrusted("action", action)),
        plain(GuiMessages.SHIFT_CLICK_ADJUST_FIFTEEN, MessageArgument.untrusted("action", action))
    ), actions);
  }

  private SettingsTile spinTile(String kind, int position, int row, int color, TextKey label, TextKey direction, int sign) {
    String name = plain(label);
    String towards = plain(direction);
    Map<SettingsClick, SettingsAction> actions = new EnumMap<>(SettingsClick.class);
    actions.put(SettingsClick.LEFT, action(ADJUST_COLOR, "spin:" + (5 * sign)));
    actions.put(SettingsClick.SHIFT_LEFT, action(ADJUST_COLOR, "spin:" + (15 * sign)));
    return colorTile(kind, position, row, adjust(color, "spin", 15 * sign), name, List.of(
        plain(GuiMessages.CLICK_SPIN_FIVE, MessageArgument.untrusted("direction", towards)),
        plain(GuiMessages.SHIFT_CLICK_SPIN_FIFTEEN, MessageArgument.untrusted("direction", towards))
    ), actions);
  }

  void handleClick(UUID viewerId, SettingsAction action) {
    Session session = viewerId == null ? null : sessions.get(viewerId);
    if (session == null || !(action instanceof SettingsAction.Custom)) {
      return;
    }
    SettingsAction.Custom custom = (SettingsAction.Custom) action;
    String value = custom.value() == null ? "" : custom.value();
    MonitorGroup group = session.group;
    switch (custom.kind()) {
      case EDIT_GROUP -> {
        int index = parseInt(value, -1);
        List<MonitorGroup> groups = session.configuration.getGroups();
        if (index >= 0 && index < groups.size()) {
          showGroup(viewerId, session, groups.get(index));
        }
      }
      case CREATE_GROUP -> promptNewGroup(viewerId, session);
      case SET_HEAD -> {
        if (group != null) {
          group.setHeadSampler(value);
          save(session);
          showGroup(viewerId, session, group);
        }
      }
      case REMOVE_SAMPLER -> {
        if (group != null) {
          boolean head = value.equals(group.getHeadOrSomething());
          group.getSamplers().remove(value);
          if (head) {
            group.setHeadSampler(null);
          }
          save(session);
          showGroup(viewerId, session, group);
        }
      }
      case ADD_SAMPLER -> {
        if (group != null) {
          showSamplerHub(viewerId, session);
        }
      }
      case RENAME_GROUP -> {
        if (group != null) {
          promptRename(viewerId, session, group);
        }
      }
      case RECOLOR_GROUP -> {
        if (group != null) {
          session.initialColor = rgb(group.getColorValue());
          showColors(viewerId, session);
        }
      }
      case DELETE_GROUP -> {
        if (group != null) {
          session.configuration.getGroups().remove(group);
          session.group = null;
          save(session);
          showGroups(viewerId, session);
        }
      }
      case PICKER_ALL -> showSamplerGroup(viewerId, session, null, 0);
      case PICKER_GROUP -> showSamplerGroup(viewerId, session, entryGroup(session, parseInt(value, -1)), 0);
      case PICKER_PAGE -> showSamplerGroup(viewerId, session, session.pickerGroup, parseInt(value, 0));
      case PICKER_HUB -> showSamplerHub(viewerId, session);
      case PICK_SAMPLER -> {
        Sampler sampler = lookup.apply(value);
        if (group != null && sampler != null) {
          group.getSamplers().add(sampler.getId());
          save(session);
          showGroup(viewerId, session, group);
        }
      }
      case PICK_COLOR -> applyColor(viewerId, session, parseInt(value, session.initialColor));
      case CUSTOM_COLOR -> {
        session.customColor = session.initialColor;
        showCustomColor(viewerId, session);
      }
      case USE_COLOR -> applyColor(viewerId, session, session.customColor);
      case ADJUST_COLOR -> {
        int split = value.indexOf(':');
        if (split > 0) {
          session.customColor = adjust(session.customColor, value.substring(0, split), parseInt(value.substring(split + 1), 0));
          showCustomColor(viewerId, session);
        }
      }
      case ENTER_HEX -> promptHex(viewerId, session);
      default -> {
      }
    }
  }

  private void closed(UUID viewerId, Session session, View view) {
    switch (view) {
      case GROUPS -> {
        save(session);
        sessions.remove(viewerId, session);
      }
      case GROUP -> {
        save(session);
        showGroups(viewerId, session);
      }
      case COLORS -> applyColor(viewerId, session, session.initialColor);
      case CUSTOM_COLOR -> applyColor(viewerId, session, session.customColor);
      default -> sessions.remove(viewerId, session);
    }
  }

  private void showGroups(UUID viewerId, Session session) {
    session.group = null;
    show(viewerId, session, View.GROUPS, () -> groupsScreen(session.configuration));
  }

  private void showGroup(UUID viewerId, Session session, MonitorGroup group) {
    group.setSamplers(new ArrayList<>(group.getSamplers()));
    session.group = group;
    show(viewerId, session, View.GROUP, () -> groupScreen(group));
  }

  private void showSamplerHub(UUID viewerId, Session session) {
    List<String> without = new ArrayList<>(session.group.getSamplers());
    show(viewerId, session, View.SAMPLER_HUB, () -> samplerHubScreen(without));
  }

  private void showSamplerGroup(UUID viewerId, Session session, ReactGuiTaxonomy.EntryGroup group, int page) {
    if (session.group == null) {
      return;
    }
    List<String> without = new ArrayList<>(session.group.getSamplers());
    session.pickerGroup = group;
    show(viewerId, session, View.SAMPLER_GROUP, () -> samplerGroupScreen(without, group, page));
  }

  private void showColors(UUID viewerId, Session session) {
    show(viewerId, session, View.COLORS, this::colorsScreen);
  }

  private void showCustomColor(UUID viewerId, Session session) {
    show(viewerId, session, View.CUSTOM_COLOR, () -> customColorScreen(session.customColor));
  }

  private void show(UUID viewerId, Session session, View view, Supplier<SettingsScreen> screen) {
    if (sessions.get(viewerId) != session) {
      return;
    }
    Object token = new Object();
    session.view = view;
    session.token = token;
    LanguageAudience.run(viewerId, () -> host.open(viewerId, screen.get(), new Handler(session, token)));
  }

  private void applyColor(UUID viewerId, Session session, int color) {
    MonitorGroup group = session.group;
    if (group == null) {
      return;
    }
    group.setColor(hex(color));
    save(session);
    showGroup(viewerId, session, group);
  }

  private void promptNewGroup(UUID viewerId, Session session) {
    detach(viewerId, session, true);
    send(viewerId, GuiMessages.GROUP_CREATE_PROMPT);
    prompts.begin(viewerId, (name) -> {
      MonitorGroup group = MonitorGroup.builder()
          .name(name)
          .color(hex(PRESET_COLORS.get(random.nextInt(PRESET_COLORS.size()))))
          .build();
      session.configuration.getGroups().add(group);
      save(session);
      showGroup(viewerId, session, group);
    }, () -> {
      send(viewerId, GuiMessages.GROUP_INVALID_NAME);
      showGroups(viewerId, session);
    });
  }

  private void promptRename(UUID viewerId, Session session, MonitorGroup group) {
    detach(viewerId, session, true);
    send(viewerId, GuiMessages.GROUP_RENAME_PROMPT);
    prompts.begin(viewerId, (name) -> {
      group.setName(name);
      save(session);
      showGroup(viewerId, session, group);
    }, () -> sessions.remove(viewerId, session));
  }

  private void promptHex(UUID viewerId, Session session) {
    detach(viewerId, session, false);
    send(viewerId, GuiMessages.COLOR_PROMPT_HEX);
    prompts.begin(viewerId, (input) -> {
      Integer parsed = parseHex(input);
      if (parsed != null) {
        session.customColor = parsed;
      }
      showCustomColor(viewerId, session);
    }, () -> sessions.remove(viewerId, session));
  }

  private void detach(UUID viewerId, Session session, boolean close) {
    session.token = null;
    session.view = View.PROMPT;
    if (close) {
      host.close(viewerId);
    }
  }

  private List<Sampler> listedSamplers(List<String> without) {
    List<Sampler> result = new ArrayList<>();
    Collection<Sampler> all = samplers.get();
    if (all == null) {
      return result;
    }
    for (Sampler sampler : all) {
      if (sampler != null && !SamplerUnknown.ID.equalsIgnoreCase(sampler.getId()) && listed.test(sampler)
          && (without == null || !without.contains(sampler.getId()))) {
        result.add(sampler);
      }
    }
    result.sort(Comparator
        .comparingInt((Sampler sampler) -> ReactGuiTaxonomy.groupOrder(sampler.getId()))
        .thenComparing((Sampler sampler) -> ReactGuiTaxonomy.normalizeSortKey(sampler.getName()))
        .thenComparing((Sampler sampler) -> ReactGuiTaxonomy.normalizeSortKey(sampler.getId())));
    return result;
  }

  private ReactGuiTaxonomy.EntryGroup entryGroup(Session session, int order) {
    List<String> without = session.group == null ? List.of() : session.group.getSamplers();
    for (Sampler sampler : listedSamplers(without)) {
      ReactGuiTaxonomy.EntryGroup group = ReactGuiTaxonomy.groupForId(sampler.getId());
      if (group.order() == order) {
        return group;
      }
    }
    return null;
  }

  private static Collection<Sampler> registeredSamplers() {
    SampleController controller = SamplerHosts.sampleController();
    return controller == null || controller.getSamplers() == null ? List.of() : controller.getSamplers().all();
  }

  static int adjust(int color, String kind, int amount) {
    TinyColor tiny = new TinyColor(new Color(color));
    TinyColor adjusted = switch (kind) {
      case "darken" -> tiny.darken(amount);
      case "brighten" -> tiny.brighten(amount);
      case "saturate" -> tiny.saturate(amount);
      case "desaturate" -> tiny.desaturate(amount);
      case "spin" -> tiny.spin(amount);
      default -> tiny;
    };
    return rgb(adjusted.getColor().getRGB());
  }

  static Integer parseHex(String input) {
    String trimmed = input == null ? "" : input.trim();
    String hex = trimmed.startsWith("#") ? trimmed : "#" + trimmed;
    try {
      return rgb(new TinyColor(hex).getColor().getRGB());
    } catch (NumberFormatException invalid) {
      return null;
    }
  }

  static String hex(int color) {
    return "#" + Integer.toHexString(new Color(color).getRGB()).substring(2);
  }

  private static int rgb(int argb) {
    return argb & 0xFFFFFF;
  }

  private static int row(int index) {
    return index / 9;
  }

  private static int position(int index) {
    return (index % 9) - 4;
  }

  private static int parseInt(String value, int fallback) {
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException invalid) {
      return fallback;
    }
  }

  private static SettingsAction action(String kind, String value) {
    return new SettingsAction.Custom(kind, value);
  }

  private static SettingsTile tile(String key, int position, int row, String icon, String name, List<String> lore,
                                   Map<SettingsClick, SettingsAction> actions) {
    return new SettingsTile(key, position, row, icon, FALLBACK_ICON, name, lore, false, actions, null);
  }

  private static SettingsTile colorTile(String key, int position, int row, int color, String name, List<String> lore,
                                        Map<SettingsClick, SettingsAction> actions) {
    return new SettingsTile(key, position, row, COLOR_ICON, FALLBACK_ICON, name, lore, false, actions, rgb(color));
  }

  private static String plain(TextKey key, MessageArgument... arguments) {
    return ReactLanguage.plain(key, arguments);
  }

  private static void send(UUID viewerId, TextKey key) {
    Object handle = ReactSettingsMenu.handle(viewerId);
    if (handle != null) {
      ReactLanguage.send(handle, key);
    }
  }

  private void save(Session session) {
    session.saver.accept(session.configuration);
  }

  enum View {
    GROUPS,
    GROUP,
    SAMPLER_HUB,
    SAMPLER_GROUP,
    COLORS,
    CUSTOM_COLOR,
    PROMPT
  }

  private static final class Session {
    private final MonitorConfiguration configuration;
    private final Consumer<MonitorConfiguration> saver;
    private volatile MonitorGroup group;
    private volatile ReactGuiTaxonomy.EntryGroup pickerGroup;
    private volatile int initialColor = CUSTOM_START_COLOR;
    private volatile int customColor = CUSTOM_START_COLOR;
    private volatile View view = View.GROUPS;
    private volatile Object token;

    private Session(MonitorConfiguration configuration, Consumer<MonitorConfiguration> saver) {
      this.configuration = configuration;
      this.saver = saver;
    }
  }

  private final class Handler implements SettingsScreenHost.ClickHandler {
    private final Session session;
    private final Object token;

    private Handler(Session session, Object token) {
      this.session = session;
      this.token = token;
    }

    @Override
    public void onClick(UUID viewerId, SettingsTile tile, SettingsClick click, SettingsAction action) {
      handleClick(viewerId, action);
    }

    @Override
    public void onClosed(UUID viewerId) {
      if (session.token != token || sessions.get(viewerId) != session) {
        return;
      }
      session.token = null;
      View view = session.view;
      LanguageAudience.run(viewerId, () -> closed(viewerId, session, view));
    }
  }
}
