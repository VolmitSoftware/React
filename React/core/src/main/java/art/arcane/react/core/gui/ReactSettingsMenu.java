package art.arcane.react.core.gui;

import art.arcane.react.api.action.Action;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.api.web.WebRevisions;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.ComponentConfigEditor;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.TweakController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.SettingsAction;
import art.arcane.react.platform.SettingsClick;
import art.arcane.react.platform.SettingsScreen;
import art.arcane.react.platform.SettingsScreenHost;
import art.arcane.react.platform.SettingsTile;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.format.C;
import art.arcane.react.util.project.config.ConfigDocumentation;
import art.arcane.react.util.project.config.TomlCodec;
import art.arcane.react.util.project.registry.Registered;
import art.arcane.react.util.project.registry.Registry;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageKey;
import com.google.gson.Gson;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * React's settings menu on every platform: the screens, what each click does, and how an edit is written to its
 * TOML file and applied. Paths look like {@code feature.entity-trimmer.enabled}.
 */
public final class ReactSettingsMenu {
  public static final String ROOT_MAIN = "main";
  public static final String ROOT_CORE = "core";
  public static final String ROOT_FEATURE = "feature";
  public static final String ROOT_TWEAK = "tweak";
  public static final String ROOT_ACTION = "action";
  public static final String ROOT_SAMPLER = "sampler";
  public static final String ROOT_ADVANCED = "advanced";
  public static final String ROOT_CATEGORY = "cat";
  public static final String BACKGROUND = "black_stained_glass_pane";

  static final String[] PRESET_MANAGED_IDS = {
      "dynamic-view-distance",
      "afk-view-shedding",
      "per-world-tick-budget",
      "random-tick-governor",
      "tracker-range-governor",
      "activation-range-governor",
      "dynamic-activation-range",
      "adaptive-entity-sleep",
      "pathfinder-budget",
      "mob-stacking",
      "item-super-stacker",
      "entity-trimmer"
  };

  private static final Gson JSON = new Gson();
  private static final String CHEST = "chest";
  private static final String CORE_FALLBACK_ICON = "slime_ball";
  private static final int MIN_CONTENT_ROWS = 1;
  private static final int MAX_CONTENT_ROWS = 5;
  private static final int PAGE_JUMP = 5;
  private static final int MAX_VALUE_PREVIEW = 64;

  private final SettingsSource source;
  private final SettingsScreenHost host;
  private final Object writeLock = new Object();

  public ReactSettingsMenu(SettingsSource source, SettingsScreenHost host) {
    this.source = Objects.requireNonNull(source, "source");
    this.host = Objects.requireNonNull(host, "host");
  }

  public SettingsSource source() {
    return source;
  }

  public boolean canConfigure(UUID viewerId) {
    return viewerId != null && source.canConfigure(viewerId);
  }

  public void open(UUID viewerId) {
    open(viewerId, "", 0);
  }

  public void open(UUID viewerId, String sectionPath, int page) {
    if (viewerId != null) {
      LanguageAudience.run(viewerId, () -> openForViewer(viewerId, sectionPath, page));
    }
  }

  private void openForViewer(UUID viewerId, String sectionPath, int page) {
    if (!canConfigure(viewerId)) {
      send(viewerId, GuiMessages.CONFIG_PERMISSION);
      return;
    }

    if (!J.isPrimaryThread()) {
      Object handle = handle(viewerId);
      if (handle != null) {
        J.runEntity(handle, () -> open(viewerId, sectionPath, page));
      }
      return;
    }

    host.playPageTurn(viewerId);
    String safePath = normalizePath(sectionPath);
    Optional<SettingsScreen> screen = screen(safePath, page);
    if (screen.isEmpty()) {
      if (safePath.equals(ROOT_MAIN)) {
        send(viewerId, GuiMessages.CONFIG_MAIN_UNAVAILABLE);
      } else {
        send(viewerId, GuiMessages.CONFIG_OPEN_FAILED, MessageArgument.untrusted("path", safePath));
      }
      return;
    }
    host.open(viewerId, screen.get(), this::handleClick);
  }

  /**
   * The screen for a menu path, empty when the path names no config section.
   */
  public Optional<SettingsScreen> screen(String sectionPath, int page) {
    String safePath = normalizePath(sectionPath);
    if (safePath.isBlank() || safePath.equals(ROOT_CATEGORY)) {
      return Optional.of(root());
    }
    if (safePath.equals(ROOT_MAIN)) {
      SectionTarget target = resolveSectionTarget(ROOT_MAIN);
      if (target == null || target.sectionObject() == null) {
        return Optional.empty();
      }
      return Optional.of(fieldScreen(ROOT_MAIN, buildEntries(ROOT_MAIN, target.sectionObject(), target.sourceTag()), page));
    }
    if (safePath.equals(ROOT_CORE)) {
      return Optional.of(coreIndex(page));
    }
    if (safePath.equals(ROOT_FEATURE)) {
      return Optional.of(featureIndex(page));
    }
    if (safePath.equals(ROOT_TWEAK)) {
      return Optional.of(tweakIndex(page));
    }
    if (safePath.equals(ROOT_ACTION)) {
      return Optional.of(actionIndex(page));
    }
    if (safePath.equals(ROOT_SAMPLER)) {
      return Optional.of(samplerIndex(page));
    }
    if (safePath.equals(ROOT_ADVANCED)) {
      return Optional.of(advanced(page));
    }
    if (safePath.startsWith(ROOT_CATEGORY + ".")) {
      return Optional.of(categoryIndex(safePath.substring(ROOT_CATEGORY.length() + 1), page));
    }

    SectionTarget target = resolveSectionTarget(safePath);
    if (target == null || target.sectionObject() == null) {
      return Optional.empty();
    }
    return Optional.of(fieldScreen(safePath, buildEntries(safePath, target.sectionObject(), target.sourceTag()), page));
  }

  public void handleClick(UUID viewerId, SettingsTile tile, SettingsClick click, SettingsAction action) {
    if (viewerId == null || action == null) {
      return;
    }
    if (action instanceof SettingsAction.Open open) {
      open(viewerId, open.path(), open.page());
    } else if (action instanceof SettingsAction.Apply apply) {
      confirmAndApply(viewerId, apply.returnPath(), apply.returnPage(), apply.valuePath(), apply.value());
    } else if (action instanceof SettingsAction.Preset preset) {
      applyPreset(viewerId, preset.preset());
    } else if (action instanceof SettingsAction.Input input) {
      beginInput(viewerId, input);
    } else if (action instanceof SettingsAction.ServerLanguage) {
      host.close(viewerId);
      source.openServerLanguage(viewerId);
    } else if (action instanceof SettingsAction.NullSection) {
      send(viewerId, GuiMessages.CONFIG_SECTION_NULL);
    }
  }

  private void beginInput(UUID viewerId, SettingsAction.Input input) {
    Optional<SettingsInputSessions> sessions = source.inputSessions();
    if (sessions.isEmpty()) {
      send(viewerId, GuiMessages.CONFIG_INPUT_UNAVAILABLE);
      return;
    }
    host.close(viewerId);
    sessions.get().begin(viewerId, input);
  }

  public void confirmAndApply(UUID actor, String returnSectionPath, int returnPage, String valuePath, Object value) {
    String path = normalizePath(valuePath);
    if (path.isBlank()) {
      return;
    }

    String section = normalizePath(returnSectionPath);
    applyAndSave(actor, path, value);
    if (actor != null) {
      open(actor, section, Math.max(0, returnPage));
    }
  }

  public boolean applyAndSave(UUID actor, String valuePath, Object value) {
    String path = normalizePath(valuePath);
    if (path.isBlank()) {
      return false;
    }

    synchronized (writeLock) {
      EditTarget target = resolveEditTarget(path);
      if (target == null) {
        send(actor, GuiMessages.CONFIG_SET_FAILED, MessageArgument.untrusted("path", path));
        return false;
      }

      Object before = readPathValue(target.rootObject(), target.objectPath());
      String beforeToml = TomlCodec.toToml(target.rootObject(), target.sourceTag());

      if (!setPathValue(target.rootObject(), target.objectPath(), value, true)) {
        send(actor, GuiMessages.CONFIG_SET_FAILED, MessageArgument.untrusted("path", path));
        return false;
      }

      try {
        String updatedToml = TomlCodec.toToml(target.rootObject(), target.sourceTag());
        source.writeConfig(target.file(), updatedToml);
      } catch (Throwable e) {
        J.attempt(() -> source.writeConfig(target.file(), beforeToml));
        target.reload().getAsBoolean();
        send(actor, GuiMessages.CONFIG_PERSIST_FAILED, MessageArgument.untrusted("reason", String.valueOf(e.getMessage())));
        return false;
      }

      if (!target.reload().getAsBoolean()) {
        J.attempt(() -> source.writeConfig(target.file(), beforeToml));
        target.reload().getAsBoolean();
        send(actor, GuiMessages.CONFIG_RELOAD_REVERTED);
        return false;
      }

      WebRevisions.bumpForConfigPath(path);
      send(
          actor,
          GuiMessages.CONFIG_UPDATED,
          MessageArgument.untrusted("path", path),
          MessageArgument.untrusted("before", summarizeValue(before)),
          MessageArgument.untrusted("after", summarizeValue(value))
      );
      return true;
    }
  }

  public int applyPresetHeadless(String preset) {
    String normalizedPreset = preset == null ? "" : preset.trim().toLowerCase(Locale.ROOT);
    boolean valid = switch (normalizedPreset) {
      case "off", "light", "balanced", "high" -> true;
      default -> false;
    };
    if (!valid) {
      return -1;
    }

    int updated = 0;
    boolean enable = !normalizedPreset.equals("off");
    for (String id : PRESET_MANAGED_IDS) {
      String enabledPath = managedEnabledPath(id);
      if (enabledPath == null) {
        continue;
      }
      if (applyAndSave(null, enabledPath, enable)) {
        updated++;
      }
    }

    if (enable && managedEnabledPath("dynamic-view-distance") != null) {
      updated += applyViewDistanceProfile(normalizedPreset);
    }

    return updated;
  }

  private void applyPreset(UUID viewerId, String preset) {
    int updated = applyPresetHeadless(preset);
    if (updated < 0) {
      send(viewerId, GuiMessages.CONFIG_PROFILE_UNKNOWN, MessageArgument.untrusted("profile", String.valueOf(preset)));
      return;
    }

    String normalizedPreset = preset == null ? "" : preset.trim().toLowerCase(Locale.ROOT);
    String presetName = switch (normalizedPreset) {
      case "off" -> ReactLanguage.plain(GuiMessages.CONFIG_PROFILE_OFF);
      case "light" -> ReactLanguage.plain(GuiMessages.CONFIG_PROFILE_LIGHT);
      case "balanced" -> ReactLanguage.plain(GuiMessages.CONFIG_PROFILE_BALANCED);
      case "high" -> ReactLanguage.plain(GuiMessages.CONFIG_PROFILE_HIGH);
      default -> throw new AssertionError("unreachable: applyPresetHeadless validated preset");
    };

    send(
        viewerId,
        GuiMessages.CONFIG_PROFILE_APPLIED,
        MessageArgument.untrusted("profile", presetName),
        MessageArgument.untrusted("count", updated)
    );
    host.open(viewerId, root(), this::handleClick);
  }

  private int applyViewDistanceProfile(String preset) {
    int warmupSeconds;
    double viewDistanceMin;
    double viewDistanceMax;
    double lerpTickTimeMin;
    double lerpTickTimeMax;
    int pressureSendViewDistanceCap;
    double pressureEngageTickTimeMs;
    long pressureSustainEngageMs;
    int pressureWarmupSeconds;
    int idleSendViewDistance;

    switch (preset) {
      case "light" -> {
        warmupSeconds = 60;
        viewDistanceMin = 10.0;
        viewDistanceMax = 16.0;
        lerpTickTimeMin = 50.0;
        lerpTickTimeMax = 170.0;
        pressureSendViewDistanceCap = 10;
        pressureEngageTickTimeMs = 85.0;
        pressureSustainEngageMs = 18000L;
        pressureWarmupSeconds = 60;
        idleSendViewDistance = 6;
      }
      case "balanced" -> {
        warmupSeconds = 45;
        viewDistanceMin = 6.0;
        viewDistanceMax = 16.0;
        lerpTickTimeMin = 45.0;
        lerpTickTimeMax = 140.0;
        pressureSendViewDistanceCap = 8;
        pressureEngageTickTimeMs = 70.0;
        pressureSustainEngageMs = 12000L;
        pressureWarmupSeconds = 45;
        idleSendViewDistance = 4;
      }
      case "high" -> {
        warmupSeconds = 30;
        viewDistanceMin = 4.0;
        viewDistanceMax = 16.0;
        lerpTickTimeMin = 35.0;
        lerpTickTimeMax = 110.0;
        pressureSendViewDistanceCap = 6;
        pressureEngageTickTimeMs = 58.0;
        pressureSustainEngageMs = 8000L;
        pressureWarmupSeconds = 30;
        idleSendViewDistance = 4;
      }
      default -> {
        return 0;
      }
    }

    int updated = 0;
    if (applyAndSave(null, "feature.dynamic-view-distance.warmupSeconds", Integer.valueOf(warmupSeconds))) {
      updated++;
    }
    if (applyAndSave(null, "feature.dynamic-view-distance.viewDistance.min", Double.valueOf(viewDistanceMin))) {
      updated++;
    }
    if (applyAndSave(null, "feature.dynamic-view-distance.viewDistance.max", Double.valueOf(viewDistanceMax))) {
      updated++;
    }
    if (applyAndSave(null, "feature.dynamic-view-distance.lerpTickTime.min", Double.valueOf(lerpTickTimeMin))) {
      updated++;
    }
    if (applyAndSave(null, "feature.dynamic-view-distance.lerpTickTime.max", Double.valueOf(lerpTickTimeMax))) {
      updated++;
    }

    if (managedEnabledPath("afk-view-shedding") != null) {
      if (applyAndSave(null, "feature.afk-view-shedding.pressureSendViewDistanceCap", Integer.valueOf(pressureSendViewDistanceCap))) {
        updated++;
      }
      if (applyAndSave(null, "feature.afk-view-shedding.pressureEngageTickTimeMs", Double.valueOf(pressureEngageTickTimeMs))) {
        updated++;
      }
      if (applyAndSave(null, "feature.afk-view-shedding.pressureSustainEngageMs", Long.valueOf(pressureSustainEngageMs))) {
        updated++;
      }
      if (applyAndSave(null, "feature.afk-view-shedding.pressureWarmupSeconds", Integer.valueOf(pressureWarmupSeconds))) {
        updated++;
      }
      if (applyAndSave(null, "feature.afk-view-shedding.idleSendViewDistance", Integer.valueOf(idleSendViewDistance))) {
        updated++;
      }
    }

    return updated;
  }

  private String managedEnabledPath(String id) {
    if (id == null || id.isBlank()) {
      return null;
    }
    if (ReactComponents.feature(id) != null) {
      return ROOT_FEATURE + "." + id + ".enabled";
    }
    if (ReactComponents.tweak(id) != null) {
      return ROOT_TWEAK + "." + id + ".enabled";
    }
    return null;
  }

  SettingsScreen root() {
    Map<String, int[]> counts = new HashMap<>();
    FeatureController featureController = ReactComponents.features();
    if (featureController != null && featureController.getFeatures() != null) {
      for (Feature feature : featureController.getFeatures().all()) {
        if (feature == null) {
          continue;
        }
        accumulateCategoryCount(counts, feature.getId(), feature.isEnabled());
      }
    }
    TweakController tweakController = ReactComponents.tweaks();
    if (tweakController != null && tweakController.getTweaks() != null) {
      for (Tweak tweak : tweakController.getTweaks().all()) {
        if (tweak == null) {
          continue;
        }
        accumulateCategoryCount(counts, tweak.getId(), tweak.isEnabled());
      }
    }

    List<SettingsTile> tiles = new ArrayList<>();
    List<ReactGuiTaxonomy.Category> categories = new ArrayList<>();
    for (ReactGuiTaxonomy.Category category : ReactGuiTaxonomy.topLevelCategories()) {
      if (counts.containsKey(category.key())) {
        categories.add(category);
      }
    }

    int categoryRowCount = Math.min(9, categories.size());
    int categoryRows = (int) Math.ceil(Math.max(1, categories.size()) / 9D);
    for (int index = 0; index < categories.size(); index++) {
      ReactGuiTaxonomy.Category category = categories.get(index);
      int row = index / 9;
      int columnCount = Math.min(9, categories.size() - (row * 9));
      int w = centeredPosition(index % 9, columnCount);
      int[] tally = counts.getOrDefault(category.key(), new int[]{0, 0});
      int enabled = tally[0];
      int total = tally[1];
      tiles.add(new SettingsTile(
          "cfg-cat-" + category.key(),
          w,
          row,
          category.icon(),
          CHEST,
          C.WHITE + category.label(),
          List.of(
              C.GRAY + category.description(),
              C.GREEN + ReactLanguage.plain(
                  GuiMessages.CONFIG_CATEGORY_ENABLED,
                  MessageArgument.untrusted("enabled", enabled),
                  MessageArgument.untrusted("total", total)
              )
          ),
          enabled > 0,
          Map.of(SettingsClick.LEFT, new SettingsAction.Open(ROOT_CATEGORY + "." + category.key(), 0))
      ));
    }

    if (categoryRowCount == 0) {
      tiles.add(plainTile("cfg-empty", 0, 0, "paper", C.GRAY + ReactLanguage.plain(GuiMessages.CONFIG_NO_MODULES)));
    }

    int presetRow = Math.max(categoryRows, 3);
    tiles.add(presetTile("off", -3, presetRow, "gray_dye", GuiMessages.CONFIG_PROFILE_OFF, GuiMessages.CONFIG_PROFILE_OFF_DESCRIPTION));
    tiles.add(presetTile("light", -1, presetRow, "lime_dye", GuiMessages.CONFIG_PROFILE_LIGHT, GuiMessages.CONFIG_PROFILE_LIGHT_DESCRIPTION));
    tiles.add(presetTile("balanced", 1, presetRow, "gold_ingot", GuiMessages.CONFIG_PROFILE_BALANCED, GuiMessages.CONFIG_PROFILE_BALANCED_DESCRIPTION));
    tiles.add(presetTile("high", 3, presetRow, "blaze_powder", GuiMessages.CONFIG_PROFILE_HIGH, GuiMessages.CONFIG_PROFILE_HIGH_DESCRIPTION));

    tiles.add(new SettingsTile(
        "cfg-root-main",
        -1,
        5,
        "comparator",
        CHEST,
        C.WHITE + ReactLanguage.plain(GuiMessages.CONFIG_MAIN),
        List.of(C.GRAY + ReactLanguage.plain(GuiMessages.CONFIG_MAIN_DESCRIPTION)),
        false,
        Map.of(SettingsClick.LEFT, new SettingsAction.Open(ROOT_MAIN, 0))
    ));
    tiles.add(new SettingsTile(
        "cfg-root-advanced",
        1,
        5,
        "command_block",
        CHEST,
        C.WHITE + ReactLanguage.plain(GuiMessages.CONFIG_ADVANCED),
        List.of(C.GRAY + ReactLanguage.plain(GuiMessages.CONFIG_ADVANCED_DESCRIPTION)),
        false,
        Map.of(SettingsClick.LEFT, new SettingsAction.Open(ROOT_ADVANCED, 0))
    ));

    return new SettingsScreen(shortenTitle(ReactLanguage.plain(GuiMessages.CONFIG_TITLE)), 6, BACKGROUND, tiles);
  }

  private static SettingsTile presetTile(String preset, int position, int row, String icon, MessageKey label, MessageKey description) {
    return new SettingsTile(
        "cfg-preset-" + preset,
        position,
        row,
        icon,
        CHEST,
        C.AQUA + ReactLanguage.plain(
            GuiMessages.CONFIG_PROFILE_TITLE,
            MessageArgument.untrusted("profile", ReactLanguage.plain(label))
        ),
        List.of(C.GRAY + ReactLanguage.plain(description)),
        false,
        Map.of(SettingsClick.LEFT, new SettingsAction.Preset(preset))
    );
  }

  private static SettingsTile plainTile(String key, int position, int row, String icon, String name) {
    return new SettingsTile(key, position, row, icon, CHEST, name, List.of(), false, Map.of());
  }

  private static void accumulateCategoryCount(Map<String, int[]> counts, String id, boolean enabled) {
    String key = ReactGuiTaxonomy.categoryForId(id).key();
    int[] tally = counts.computeIfAbsent(key, (k) -> new int[]{0, 0});
    tally[1]++;
    if (enabled) {
      tally[0]++;
    }
  }

  private SettingsScreen advanced(int page) {
    int coreCount = source.coreSections().size();
    FeatureController featureController = ReactComponents.features();
    int featureCount = featureController != null && featureController.getFeatures() != null
        ? featureController.getFeatures().size()
        : 0;
    int activeFeatureCount = featureController != null && featureController.getActiveFeatures() != null
        ? featureController.getActiveFeatures().size()
        : 0;
    TweakController tweakController = ReactComponents.tweaks();
    int tweakCount = tweakController != null && tweakController.getTweaks() != null
        ? tweakController.getTweaks().size()
        : 0;
    int activeTweakCount = tweakController != null && tweakController.getActiveTweaks() != null
        ? tweakController.getActiveTweaks().size()
        : 0;
    ActionController actionController = ReactComponents.actions();
    int actionCount = actionController != null && actionController.getActions() != null
        ? actionController.getActions().size()
        : 0;
    SampleController sampleController = source.samples();
    int samplerCount = sampleController != null && sampleController.getSamplers() != null
        ? sampleController.getSamplers().size()
        : 0;

    List<SectionIndexEntry> entries = new ArrayList<>();
    entries.add(new SectionIndexEntry(ROOT_MAIN, ReactLanguage.plain(GuiMessages.CONFIG_MAIN), "comparator", ""));
    entries.add(new SectionIndexEntry(
        ROOT_CORE,
        ReactLanguage.plain(GuiMessages.CONFIG_CORE_COUNT, MessageArgument.untrusted("count", coreCount)),
        "redstone",
        "",
        coreCount > 0
    ));
    entries.add(new SectionIndexEntry(
        ROOT_FEATURE,
        ReactLanguage.plain(
            GuiMessages.CONFIG_FEATURE_COUNT,
            MessageArgument.untrusted("active", activeFeatureCount),
            MessageArgument.untrusted("total", featureCount)
        ),
        "nether_star",
        "",
        activeFeatureCount > 0
    ));
    entries.add(new SectionIndexEntry(
        ROOT_TWEAK,
        ReactLanguage.plain(
            GuiMessages.CONFIG_TWEAK_COUNT,
            MessageArgument.untrusted("active", activeTweakCount),
            MessageArgument.untrusted("total", tweakCount)
        ),
        "blaze_powder",
        "",
        activeTweakCount > 0
    ));
    entries.add(new SectionIndexEntry(
        ROOT_ACTION,
        ReactLanguage.plain(GuiMessages.CONFIG_ACTION_COUNT, MessageArgument.untrusted("count", actionCount)),
        "anvil",
        "",
        actionCount > 0
    ));
    entries.add(new SectionIndexEntry(
        ROOT_SAMPLER,
        ReactLanguage.plain(GuiMessages.CONFIG_SAMPLER_COUNT, MessageArgument.untrusted("count", samplerCount)),
        "clock",
        "",
        samplerCount > 0
    ));
    return sectionIndex(ROOT_ADVANCED, page, ReactLanguage.plain(GuiMessages.CONFIG_ADVANCED_TITLE), entries);
  }

  private SettingsScreen categoryIndex(String categoryKey, int page) {
    String label = categoryKey;
    for (ReactGuiTaxonomy.Category category : ReactGuiTaxonomy.topLevelCategories()) {
      if (category.key().equals(categoryKey)) {
        label = category.label();
        break;
      }
    }

    List<SectionIndexEntry> entries = new ArrayList<>();
    FeatureController featureController = ReactComponents.features();
    Registry<Feature> featureRegistry = featureController == null ? null : featureController.getFeatures();
    if (featureRegistry != null) {
      for (Feature feature : featureRegistry.all()) {
        if (feature == null || !ReactGuiTaxonomy.categoryForId(feature.getId()).key().equals(categoryKey)) {
          continue;
        }
        boolean active = featureController.getActiveFeatures() != null && featureController.getActiveFeatures().containsKey(feature.getId());
        entries.add(componentEntry(ROOT_FEATURE, feature, active, feature.isEnabled(), false));
      }
    }

    TweakController tweakController = ReactComponents.tweaks();
    Registry<Tweak> tweakRegistry = tweakController == null ? null : tweakController.getTweaks();
    if (tweakRegistry != null) {
      for (Tweak tweak : tweakRegistry.all()) {
        if (tweak == null || !ReactGuiTaxonomy.categoryForId(tweak.getId()).key().equals(categoryKey)) {
          continue;
        }
        boolean active = tweakController.getActiveTweaks() != null && tweakController.getActiveTweaks().containsKey(tweak.getId());
        entries.add(componentEntry(ROOT_TWEAK, tweak, active, tweak.isEnabled(), false));
      }
    }

    entries.sort(Comparator.comparing(e -> normalizeSortKey(e.displayName())));
    return sectionIndex(
        ROOT_CATEGORY + "." + categoryKey,
        page,
        ReactLanguage.plain(GuiMessages.CONFIG_SECTION_TITLE, MessageArgument.untrusted("section", label)),
        entries
    );
  }

  private static SectionIndexEntry componentEntry(String root, Registered component, boolean active, boolean enabled, boolean withGroup) {
    String lore = !withGroup
        ? ReactLanguage.plain(
            GuiMessages.CONFIG_LORE_STATUS_PATH,
            MessageArgument.untrusted("status", configurationStatus(active)),
            MessageArgument.untrusted("path", root + "/" + component.getId() + ".toml")
        )
        : ReactLanguage.plain(
            GuiMessages.CONFIG_LORE_STATUS_GROUP_PATH,
            MessageArgument.untrusted("status", configurationStatus(active)),
            MessageArgument.untrusted("group", ReactGuiTaxonomy.groupLabel(component.getId())),
            MessageArgument.untrusted("path", root + "/" + component.getId() + ".toml")
        );
    return new SectionIndexEntry(
        root + "." + component.getId(),
        C.stripColor(component.getName()),
        ReactGuiTaxonomy.iconForId(component.getId()),
        CHEST,
        lore,
        active,
        root + "." + component.getId() + ".enabled",
        enabled
    );
  }

  private SettingsScreen coreIndex(int page) {
    List<SectionIndexEntry> entries = new ArrayList<>();
    for (SettingsSource.CoreSection section : source.coreSections()) {
      entries.add(new SectionIndexEntry(
          ROOT_CORE + "." + section.id(),
          displayName(section.name()),
          section.icon(),
          CORE_FALLBACK_ICON,
          ReactLanguage.plain(
              GuiMessages.CONFIG_LORE_CONFIGURE_PATH,
              MessageArgument.untrusted("path", "core/" + section.id() + ".toml")
          ),
          false,
          null,
          null
      ));
    }

    entries.sort(Comparator.comparing(e -> normalizeSortKey(e.displayName())));
    return sectionIndex(
        ROOT_CORE,
        page,
        ReactLanguage.plain(
            GuiMessages.CONFIG_SECTION_TITLE,
            MessageArgument.untrusted("section", ReactLanguage.plain(GuiMessages.CONFIG_CORE))
        ),
        entries
    );
  }

  private SettingsScreen featureIndex(int page) {
    List<SectionIndexEntry> entries = new ArrayList<>();
    FeatureController controller = ReactComponents.features();
    Registry<Feature> registry = controller == null ? null : controller.getFeatures();
    if (registry != null) {
      for (Feature feature : registry.all()) {
        if (feature == null) {
          continue;
        }
        boolean active = controller.getActiveFeatures() != null && controller.getActiveFeatures().containsKey(feature.getId());
        entries.add(componentEntry(ROOT_FEATURE, feature, active, feature.isEnabled(), true));
      }
    }

    entries.sort(groupedIndexComparator(ROOT_FEATURE));
    return sectionIndex(
        ROOT_FEATURE,
        page,
        ReactLanguage.plain(
            GuiMessages.CONFIG_SECTION_TITLE,
            MessageArgument.untrusted("section", ReactLanguage.plain(GuiMessages.CONFIG_FEATURES))
        ),
        entries
    );
  }

  private SettingsScreen tweakIndex(int page) {
    List<SectionIndexEntry> entries = new ArrayList<>();
    TweakController controller = ReactComponents.tweaks();
    Registry<Tweak> registry = controller == null ? null : controller.getTweaks();
    if (registry != null) {
      for (Tweak tweak : registry.all()) {
        if (tweak == null) {
          continue;
        }
        boolean active = controller.getActiveTweaks() != null && controller.getActiveTweaks().containsKey(tweak.getId());
        entries.add(componentEntry(ROOT_TWEAK, tweak, active, tweak.isEnabled(), true));
      }
    }

    entries.sort(groupedIndexComparator(ROOT_TWEAK));
    return sectionIndex(
        ROOT_TWEAK,
        page,
        ReactLanguage.plain(
            GuiMessages.CONFIG_SECTION_TITLE,
            MessageArgument.untrusted("section", ReactLanguage.plain(GuiMessages.CONFIG_TWEAKS))
        ),
        entries
    );
  }

  private SettingsScreen actionIndex(int page) {
    List<SectionIndexEntry> entries = new ArrayList<>();
    ActionController controller = ReactComponents.actions();
    Registry<Action<?>> registry = controller == null ? null : controller.getActions();
    if (registry != null) {
      for (Action<?> action : registry.all()) {
        if (action == null) {
          continue;
        }
        entries.add(groupEntry(ROOT_ACTION, action));
      }
    }

    entries.sort(groupedIndexComparator(ROOT_ACTION));
    return sectionIndex(
        ROOT_ACTION,
        page,
        ReactLanguage.plain(
            GuiMessages.CONFIG_SECTION_TITLE,
            MessageArgument.untrusted("section", ReactLanguage.plain(GuiMessages.CONFIG_ACTIONS))
        ),
        entries
    );
  }

  private SettingsScreen samplerIndex(int page) {
    List<SectionIndexEntry> entries = new ArrayList<>();
    SampleController controller = source.samples();
    Registry<Sampler> registry = controller == null ? null : controller.getSamplers();
    if (registry != null) {
      for (Sampler sampler : registry.all()) {
        if (sampler == null) {
          continue;
        }
        entries.add(groupEntry(ROOT_SAMPLER, sampler));
      }
    }

    entries.sort(groupedIndexComparator(ROOT_SAMPLER));
    return sectionIndex(
        ROOT_SAMPLER,
        page,
        ReactLanguage.plain(
            GuiMessages.CONFIG_SECTION_TITLE,
            MessageArgument.untrusted("section", ReactLanguage.plain(GuiMessages.CONFIG_SAMPLERS))
        ),
        entries
    );
  }

  private static SectionIndexEntry groupEntry(String root, Registered component) {
    return new SectionIndexEntry(
        root + "." + component.getId(),
        C.stripColor(component.getName()),
        ReactGuiTaxonomy.iconForId(component.getId()),
        CHEST,
        ReactLanguage.plain(
            GuiMessages.CONFIG_LORE_GROUP_PATH,
            MessageArgument.untrusted("group", ReactGuiTaxonomy.groupLabel(component.getId())),
            MessageArgument.untrusted("path", root + "/" + component.getId() + ".toml")
        ),
        false,
        null,
        null
    );
  }

  private SettingsScreen sectionIndex(String path, int page, String title, List<SectionIndexEntry> entries) {
    String safePath = normalizePath(path);
    boolean includeBack = !safePath.isBlank();
    PageLayout layout = pageLayout(entries.size(), includeBack);
    int pageCount = layout.pageCount();
    int currentPage = clampPage(page, pageCount);
    int start = currentPage * layout.itemsPerPage();
    int end = Math.min(entries.size(), start + layout.itemsPerPage());

    List<SettingsTile> tiles = new ArrayList<>();
    if (entries.isEmpty()) {
      tiles.add(plainTile("cfg-empty", 0, 0, "paper", C.GRAY + ReactLanguage.plain(GuiMessages.CONFIG_NO_ENTRIES)));
    } else {
      for (int row = 0; row < layout.contentRows(); row++) {
        int rowStart = start + (row * 9);
        if (rowStart >= end) {
          break;
        }

        int rowCount = Math.min(9, end - rowStart);
        for (int i = 0; i < rowCount; i++) {
          SectionIndexEntry entry = entries.get(rowStart + i);
          String displayName = C.WHITE + entry.displayName();
          boolean quickToggle = entry.quickTogglePath() != null && entry.quickToggleValue() != null;
          if (quickToggle) {
            String status = entry.quickToggleValue()
                ? C.GREEN + ReactLanguage.plain(GuiMessages.CONFIG_STATUS_ON)
                : C.RED + ReactLanguage.plain(GuiMessages.CONFIG_STATUS_OFF);
            displayName += C.GRAY + " [" + status + C.GRAY + "]";
          }

          List<String> lore = new ArrayList<>();
          if (entry.lore() != null && !entry.lore().isBlank()) {
            lore.add(C.GRAY + entry.lore());
          }
          lore.add(C.DARK_GRAY + ReactLanguage.plain(GuiMessages.CONFIG_PATH, MessageArgument.untrusted("path", entry.path())));
          Map<SettingsClick, SettingsAction> actions = new EnumMap<>(SettingsClick.class);
          actions.put(SettingsClick.LEFT, new SettingsAction.Open(entry.path(), 0));
          if (quickToggle) {
            lore.add(C.GREEN + ReactLanguage.plain(GuiMessages.CONFIG_TOGGLE_ENABLED));
            actions.put(SettingsClick.RIGHT, new SettingsAction.Apply(safePath, currentPage, entry.quickTogglePath(), !entry.quickToggleValue()));
          }
          tiles.add(new SettingsTile(
              "cfg-index-" + entry.path(),
              centeredPosition(i, rowCount),
              row,
              entry.icon(),
              entry.fallbackIcon(),
              displayName,
              lore,
              entry.highlighted(),
              actions
          ));
        }
      }
    }

    addControls(tiles, safePath, currentPage, layout, entries.size(), start, end);
    return new SettingsScreen(shortenTitle(title), layout.rows(), BACKGROUND, tiles);
  }

  private SettingsScreen fieldScreen(String sectionPath, List<FieldEntry> entries, int page) {
    String safePath = normalizePath(sectionPath);
    boolean includeBack = !safePath.isBlank();
    PageLayout layout = pageLayout(entries.size(), includeBack);
    int pageCount = layout.pageCount();
    int currentPage = clampPage(page, pageCount);
    int start = currentPage * layout.itemsPerPage();
    int end = Math.min(entries.size(), start + layout.itemsPerPage());
    String section = safePath.isBlank() ? ReactLanguage.plain(GuiMessages.CONFIG_ROOT) : safePath;
    String title = ReactLanguage.plain(
        GuiMessages.CONFIG_SECTION_TITLE,
        MessageArgument.untrusted("section", section)
    );

    List<SettingsTile> tiles = new ArrayList<>();
    if (entries.isEmpty()) {
      tiles.add(plainTile("cfg-empty", 0, 0, "paper", C.GRAY + ReactLanguage.plain(GuiMessages.CONFIG_NO_SETTINGS)));
    } else {
      for (int row = 0; row < layout.contentRows(); row++) {
        int rowStart = start + (row * 9);
        if (rowStart >= end) {
          break;
        }

        int rowCount = Math.min(9, end - rowStart);
        for (int i = 0; i < rowCount; i++) {
          tiles.add(fieldTile(safePath, currentPage, entries.get(rowStart + i), centeredPosition(i, rowCount), row));
        }
      }
    }

    addControls(tiles, safePath, currentPage, layout, entries.size(), start, end);
    return new SettingsScreen(shortenTitle(title), layout.rows(), BACKGROUND, tiles);
  }

  private static void addControls(
      List<SettingsTile> tiles,
      String safePath,
      int currentPage,
      PageLayout layout,
      int totalEntries,
      int start,
      int end
  ) {
    int navRow = layout.controlRow();
    if (layout.includeBack()) {
      tiles.add(new SettingsTile("cfg-back", 4, navRow, "arrow", CHEST, C.GRAY + ReactLanguage.plain(GuiMessages.BACK), List.of(), false,
          Map.of(SettingsClick.LEFT, new SettingsAction.Open(parentPath(safePath), 0))));
    }

    if (!layout.pagination()) {
      return;
    }

    int pageCount = layout.pageCount();
    int prevPos = layout.includeBack() ? 1 : 2;
    int infoPos = layout.includeBack() ? 2 : 3;
    int nextPos = layout.includeBack() ? 3 : 4;
    int jumpBack = Math.max(0, currentPage - PAGE_JUMP);
    int jumpForward = Math.min(pageCount - 1, currentPage + PAGE_JUMP);

    if (currentPage > 0) {
      tiles.add(new SettingsTile("cfg-prev", prevPos, navRow, "arrow", CHEST, C.WHITE + ReactLanguage.plain(GuiMessages.PREVIOUS), List.of(), false,
          Map.of(
              SettingsClick.LEFT, new SettingsAction.Open(safePath, currentPage - 1),
              SettingsClick.RIGHT, new SettingsAction.Open(safePath, jumpBack)
          )));
    }

    if (currentPage < pageCount - 1) {
      tiles.add(new SettingsTile("cfg-next", nextPos, navRow, "arrow", CHEST, C.WHITE + ReactLanguage.plain(GuiMessages.NEXT), List.of(), false,
          Map.of(
              SettingsClick.LEFT, new SettingsAction.Open(safePath, currentPage + 1),
              SettingsClick.RIGHT, new SettingsAction.Open(safePath, jumpForward)
          )));
    }

    int from = totalEntries <= 0 ? 0 : (start + 1);
    int to = totalEntries <= 0 ? 0 : end;
    tiles.add(plainTile("cfg-page-info", infoPos, navRow, "paper", C.AQUA + ReactLanguage.plain(
        GuiMessages.PAGE,
        MessageArgument.untrusted("current", currentPage + 1),
        MessageArgument.untrusted("total", pageCount),
        MessageArgument.untrusted("from", from),
        MessageArgument.untrusted("to", to)
    )));
  }

  private SettingsTile fieldTile(String sectionPath, int currentPage, FieldEntry entry, int position, int row) {
    String typePrefix = switch (entry.kind()) {
      case BOOLEAN -> C.GREEN + ReactLanguage.plain(GuiMessages.CONFIG_TYPE_BOOLEAN);
      case NUMBER -> C.AQUA + ReactLanguage.plain(GuiMessages.CONFIG_TYPE_NUMBER);
      case STRING -> C.YELLOW + ReactLanguage.plain(GuiMessages.CONFIG_TYPE_TEXT);
      case ENUM -> C.LIGHT_PURPLE + ReactLanguage.plain(GuiMessages.CONFIG_TYPE_ENUM);
      case SECTION -> C.BLUE + ReactLanguage.plain(GuiMessages.CONFIG_TYPE_SECTION);
      case MAP -> C.GOLD + ReactLanguage.plain(GuiMessages.CONFIG_TYPE_MAP);
      case LIST -> C.GOLD + ReactLanguage.plain(GuiMessages.CONFIG_TYPE_LIST);
      case UNSUPPORTED -> C.RED + ReactLanguage.plain(GuiMessages.CONFIG_TYPE_UNSUPPORTED);
    };

    String name = displayName(entry.field().getName());
    String value = switch (entry.kind()) {
      case BOOLEAN ->
          Boolean.TRUE.equals(entry.value())
              ? ReactLanguage.plain(GuiMessages.CONFIG_VALUE_ENABLED)
              : ReactLanguage.plain(GuiMessages.CONFIG_VALUE_DISABLED);
      case SECTION -> "";
      default -> isLanguageEntry(entry)
          ? entry.value() + " - " + ReactLanguage.localeDisplayName(String.valueOf(entry.value()))
          : summarizeValue(entry.value());
    };
    String displayName = typePrefix + C.WHITE + name + (value.isBlank() ? "" : C.GRAY + " = " + C.AQUA + value);
    boolean enchanted = entry.kind() == ElementKind.BOOLEAN && Boolean.TRUE.equals(entry.value());
    Map<SettingsClick, SettingsAction> actions = new EnumMap<>(SettingsClick.class);

    switch (entry.kind()) {
      case BOOLEAN -> actions.put(SettingsClick.LEFT,
          new SettingsAction.Apply(sectionPath, currentPage, entry.path(), !Boolean.TRUE.equals(entry.value())));
      case ENUM -> {
        Object next = cycleEnum(entry.field().getType(), entry.value(), 1);
        if (next != null) {
          actions.put(SettingsClick.LEFT, new SettingsAction.Apply(sectionPath, currentPage, entry.path(), next));
        }
        Object previous = cycleEnum(entry.field().getType(), entry.value(), -1);
        if (previous != null) {
          actions.put(SettingsClick.RIGHT, new SettingsAction.Apply(sectionPath, currentPage, entry.path(), previous));
        }
      }
      case NUMBER, STRING -> actions.put(SettingsClick.LEFT, isLanguageEntry(entry)
          ? new SettingsAction.ServerLanguage()
          : new SettingsAction.Input(sectionPath, currentPage, entry.path(), entry.field().getType(), name));
      case SECTION -> {
        if (entry.value() != null) {
          int nested = getSerializableFields(entry.value().getClass()).size();
          displayName = typePrefix + C.WHITE + name + C.GRAY + " (" + C.AQUA + nested + C.GRAY + ")";
          actions.put(SettingsClick.LEFT, new SettingsAction.Open(entry.path(), 0));
        } else {
          displayName = typePrefix + C.WHITE + name + C.GRAY + " (" + C.RED + ReactLanguage.plain(GuiMessages.NULL_VALUE) + C.GRAY + ")";
          actions.put(SettingsClick.LEFT, new SettingsAction.NullSection());
        }
      }
      case MAP, LIST, UNSUPPORTED -> {
      }
    }

    return new SettingsTile("cfg-" + entry.path(), position, row, iconFor(entry), CHEST, displayName, List.of(), enchanted, actions);
  }

  private static String iconFor(FieldEntry entry) {
    return switch (entry.kind()) {
      case BOOLEAN -> Boolean.TRUE.equals(entry.value()) ? "lime_dye" : "gray_dye";
      case NUMBER -> "clock";
      case STRING -> "name_tag";
      case ENUM -> "book";
      case SECTION -> iconForSection(entry.field().getName());
      case MAP -> "chest_minecart";
      case LIST -> "barrel";
      case UNSUPPORTED -> "barrier";
    };
  }

  private static String iconForSection(String name) {
    String key = normalizeSortKey(name);
    if (key.contains("monitor") || key.contains("hud") || key.contains("display")) {
      return "compass";
    }
    if (key.contains("debug") || key.contains("verbose")) {
      return "spyglass";
    }
    if (key.contains("thread") || key.contains("tick") || key.contains("performance")) {
      return "redstone";
    }
    if (key.contains("entity") || key.contains("mob")) {
      return "zombie_head";
    }
    if (key.contains("chunk") || key.contains("world")) {
      return "grass_block";
    }
    if (key.contains("memory") || key.contains("gc")) {
      return "slime_block";
    }
    return CHEST;
  }

  private List<FieldEntry> buildEntries(String sectionPath, Object sectionObject, String sourceTag) {
    List<FieldEntry> sections = new ArrayList<>();
    List<FieldEntry> values = new ArrayList<>();
    for (Field field : getSerializableFields(sectionObject.getClass())) {
      Object value = getFieldValue(field, sectionObject);
      if (!ConfigDocumentation.shouldExposeField(sourceTag, sectionPath, field, value)) {
        continue;
      }

      String childPath = joinPath(sectionPath, field.getName());
      ElementKind kind = describe(field, value);
      FieldEntry entry = new FieldEntry(field, childPath, value, kind);
      if (kind == ElementKind.SECTION) {
        sections.add(entry);
      } else {
        values.add(entry);
      }
    }

    sections.sort(Comparator.comparing(e -> normalizeSortKey(e.field().getName())));
    values.sort(Comparator.comparing(e -> normalizeSortKey(e.field().getName())));
    sections.addAll(values);
    return sections;
  }

  private static ElementKind describe(Field field, Object value) {
    Class<?> type = normalizeType(field.getType());
    if (type == Boolean.class) {
      return ElementKind.BOOLEAN;
    }
    if (isNumericType(type)) {
      return ElementKind.NUMBER;
    }
    if (type == String.class || type == Character.class) {
      return ElementKind.STRING;
    }
    if (type.isEnum()) {
      return ElementKind.ENUM;
    }
    if (Map.class.isAssignableFrom(type)) {
      return ElementKind.MAP;
    }
    if (Collection.class.isAssignableFrom(type) || type.isArray()) {
      return ElementKind.LIST;
    }
    if (value != null || isSectionType(type)) {
      return ElementKind.SECTION;
    }
    return ElementKind.UNSUPPORTED;
  }

  private SectionTarget resolveSectionTarget(String path) {
    String normalized = normalizePath(path);
    if (normalized.isBlank()) {
      return null;
    }

    if (normalized.equals(ROOT_MAIN) || normalized.startsWith(ROOT_MAIN + ".")) {
      return new SectionTarget("main-config", resolveSectionObject(source.mainSettings(), stripPrefix(normalized, ROOT_MAIN), false));
    }

    String[] split = splitRoot(normalized);
    if (split == null) {
      return null;
    }
    Object root = rootObject(split[0], split[1]);
    if (root == null) {
      return null;
    }
    return new SectionTarget(split[0] + ":" + split[1], resolveSectionObject(root, split[2], false));
  }

  /**
   * Splits {@code feature.id.rest} into the root, the id and the rest, or null when the path names no component.
   */
  private static String[] splitRoot(String normalized) {
    for (String root : new String[]{ROOT_CORE, ROOT_FEATURE, ROOT_TWEAK, ROOT_ACTION, ROOT_SAMPLER}) {
      if (normalized.equals(root) || normalized.startsWith(root + ".")) {
        String payload = stripPrefix(normalized, root);
        if (payload.isBlank()) {
          return null;
        }
        String[] parts = payload.split("\\Q.\\E", 2);
        return new String[]{root, parts[0], parts.length > 1 ? parts[1] : ""};
      }
    }
    return null;
  }

  private Object rootObject(String root, String id) {
    if (id == null || id.isBlank()) {
      return null;
    }
    return switch (root) {
      case ROOT_CORE -> {
        for (SettingsSource.CoreSection section : source.coreSections()) {
          if (section.id().equals(id)) {
            yield section.config();
          }
        }
        yield null;
      }
      case ROOT_FEATURE -> ReactComponents.feature(id);
      case ROOT_TWEAK -> ReactComponents.tweak(id);
      case ROOT_ACTION -> ReactComponents.action(id);
      case ROOT_SAMPLER -> {
        SampleController controller = source.samples();
        yield controller == null || controller.getSamplers() == null ? null : controller.getSamplers().get(id);
      }
      default -> null;
    };
  }

  private EditTarget resolveEditTarget(String path) {
    String normalized = normalizePath(path);
    if (normalized.isBlank()) {
      return null;
    }

    if (normalized.equals(ROOT_MAIN) || normalized.startsWith(ROOT_MAIN + ".")) {
      String objectPath = stripPrefix(normalized, ROOT_MAIN);
      Object main = source.mainSettings();
      if (objectPath.isBlank() || main == null) {
        return null;
      }
      return new EditTarget(
          "main-config",
          JSON.fromJson(JSON.toJsonTree(main), main.getClass()),
          objectPath,
          source.mainFile(),
          source::reloadMain
      );
    }

    String[] split = splitRoot(normalized);
    if (split == null || split[2].isBlank()) {
      return null;
    }
    String root = split[0];
    String id = split[1];
    Object target = rootObject(root, id);
    if (target == null) {
      return null;
    }
    return new EditTarget(
        root + ":" + id,
        target,
        split[2],
        source.dataFile(root, id + ".toml"),
        () -> reload(root, id, target)
    );
  }

  private boolean reload(String root, String id, Object target) {
    return switch (root) {
      case ROOT_CORE -> runSync(() -> source.reloadCore(id));
      case ROOT_FEATURE, ROOT_TWEAK, ROOT_ACTION -> runSync(() -> ComponentConfigEditor.reload((Registered) target));
      case ROOT_SAMPLER -> runSync(() -> {
        SampleController controller = source.samples();
        if (controller == null || !controller.reloadSamplerConfig(id)) {
          return false;
        }
        source.afterSamplerReload();
        return true;
      });
      default -> false;
    };
  }

  private static boolean runSync(BooleanSupplier supplier) {
    Boolean result = J.sResult(() -> {
      try {
        return supplier.getAsBoolean();
      } catch (Throwable e) {
        ReactPlatform platform = ReactPlatforms.getOrNull();
        if (platform != null) {
          platform.warn("Config GUI apply failed: " + e.getMessage(), e);
        }
        return false;
      }
    });
    return Boolean.TRUE.equals(result);
  }

  private void send(UUID viewerId, MessageKey key, MessageArgument... arguments) {
    Object handle = handle(viewerId);
    if (handle != null) {
      ReactLanguage.send(handle, key, arguments);
    }
  }

  static Object handle(UUID viewerId) {
    ReactPlatform platform = viewerId == null ? null : ReactPlatforms.getOrNull();
    return platform == null ? null : platform.player(viewerId).map(ReactPlayer::handle).orElse(null);
  }

  public static ParseResult parseInputValue(Class<?> type, String raw) {
    if (type == null) {
      return ParseResult.fail(ReactLanguage.plain(GuiMessages.INPUT_UNKNOWN_TARGET_TYPE));
    }

    Class<?> normalized = normalizeType(type);
    String trimmed = raw == null ? "" : raw.trim();

    try {
      if (normalized == String.class) {
        return ParseResult.ok(raw == null ? "" : raw);
      }

      if (normalized == Character.class) {
        if (trimmed.length() != 1) {
          return ParseResult.fail(ReactLanguage.plain(GuiMessages.INPUT_EXPECTED_CHARACTER));
        }
        return ParseResult.ok(trimmed.charAt(0));
      }

      if (normalized == Boolean.class) {
        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("yes") || trimmed.equalsIgnoreCase("on")) {
          return ParseResult.ok(true);
        }
        if (trimmed.equalsIgnoreCase("false") || trimmed.equalsIgnoreCase("no") || trimmed.equalsIgnoreCase("off")) {
          return ParseResult.ok(false);
        }
        return ParseResult.fail(ReactLanguage.plain(GuiMessages.INPUT_EXPECTED_BOOLEAN));
      }

      if (normalized.isEnum()) {
        Object constant = parseEnumConstant(normalized, trimmed);
        if (constant == null) {
          return ParseResult.fail(ReactLanguage.plain(
              GuiMessages.INPUT_EXPECTED_ENUM,
              MessageArgument.untrusted("options", enumConstants(normalized))
          ));
        }
        return ParseResult.ok(constant);
      }

      if (normalized == Integer.class) {
        return ParseResult.ok(Integer.parseInt(trimmed));
      }
      if (normalized == Long.class) {
        return ParseResult.ok(Long.parseLong(trimmed));
      }
      if (normalized == Double.class) {
        double v = Double.parseDouble(trimmed);
        if (!Double.isFinite(v)) {
          return ParseResult.fail(ReactLanguage.plain(GuiMessages.INPUT_EXPECTED_FINITE_NUMBER));
        }
        return ParseResult.ok(v);
      }
      if (normalized == Float.class) {
        float v = Float.parseFloat(trimmed);
        if (!Float.isFinite(v)) {
          return ParseResult.fail(ReactLanguage.plain(GuiMessages.INPUT_EXPECTED_FINITE_NUMBER));
        }
        return ParseResult.ok(v);
      }
      if (normalized == Short.class) {
        return ParseResult.ok(Short.parseShort(trimmed));
      }
      if (normalized == Byte.class) {
        return ParseResult.ok(Byte.parseByte(trimmed));
      }
    } catch (Throwable e) {
      return ParseResult.fail(ReactLanguage.plain(
          GuiMessages.INPUT_INVALID_TYPE,
          MessageArgument.untrusted("type", typeName(type))
      ));
    }

    return ParseResult.fail(ReactLanguage.plain(
        GuiMessages.INPUT_UNSUPPORTED_TYPE,
        MessageArgument.untrusted("type", typeName(type))
    ));
  }

  public static String typeName(Class<?> type) {
    if (type == null) {
      return ReactLanguage.plain(GuiMessages.INPUT_TYPE_UNKNOWN);
    }

    Class<?> normalized = normalizeType(type);
    if (normalized.isEnum()) {
      return ReactLanguage.plain(GuiMessages.INPUT_TYPE_ENUM);
    }
    return normalized.getSimpleName().toLowerCase(Locale.ROOT);
  }

  static String normalizePath(String path) {
    if (path == null) {
      return "";
    }

    String normalized = path.trim();
    while (normalized.startsWith(".")) {
      normalized = normalized.substring(1);
    }
    while (normalized.endsWith(".")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private static String stripPrefix(String path, String prefix) {
    String normalized = normalizePath(path);
    if (normalized.equals(prefix)) {
      return "";
    }
    if (normalized.startsWith(prefix + ".")) {
      return normalized.substring(prefix.length() + 1);
    }
    return normalized;
  }

  static String parentPath(String path) {
    String normalized = normalizePath(path);
    if (normalized.isBlank()) {
      return "";
    }

    if (normalized.equals(ROOT_MAIN)
        || normalized.equals(ROOT_CORE)
        || normalized.equals(ROOT_FEATURE)
        || normalized.equals(ROOT_TWEAK)
        || normalized.equals(ROOT_ACTION)
        || normalized.equals(ROOT_SAMPLER)) {
      return "";
    }

    int dot = normalized.lastIndexOf('.');
    if (dot < 0) {
      return "";
    }
    return normalized.substring(0, dot);
  }

  private static String joinPath(String base, String child) {
    String left = normalizePath(base);
    if (left.isBlank()) {
      return child;
    }
    return left + "." + child;
  }

  private static Object resolveSectionObject(Object root, String sectionPath, boolean createMissing) {
    if (root == null) {
      return null;
    }

    String normalized = normalizePath(sectionPath);
    if (normalized.isBlank()) {
      return root;
    }

    Object current = root;
    for (String segment : normalized.split("\\Q.\\E")) {
      Field field = findField(current.getClass(), segment);
      if (field == null) {
        return null;
      }

      Object next = getFieldValue(field, current);
      if (next == null && createMissing) {
        next = instantiate(field.getType());
        if (next == null) {
          return null;
        }
        if (!setFieldValue(field, current, next)) {
          return null;
        }
      }

      if (next == null) {
        return null;
      }

      current = next;
    }

    return current;
  }

  private static boolean setPathValue(Object root, String path, Object value, boolean createMissing) {
    String[] segments = normalizePath(path).split("\\Q.\\E");
    if (segments.length == 0 || segments[0].isBlank()) {
      return false;
    }

    Object section = resolveSectionObject(root, String.join(".", List.of(segments).subList(0, segments.length - 1)), createMissing);
    if (section == null) {
      return false;
    }

    Field targetField = findField(section.getClass(), segments[segments.length - 1]);
    if (targetField == null) {
      return false;
    }

    Object typedValue = coerceValue(value, targetField.getType());
    return setFieldValue(targetField, section, typedValue);
  }

  private static Object readPathValue(Object root, String path) {
    String[] segments = normalizePath(path).split("\\Q.\\E");
    if (segments.length == 0 || segments[0].isBlank()) {
      return null;
    }

    Object section = resolveSectionObject(root, String.join(".", List.of(segments).subList(0, segments.length - 1)), false);
    if (section == null) {
      return null;
    }

    Field field = findField(section.getClass(), segments[segments.length - 1]);
    if (field == null) {
      return null;
    }

    return getFieldValue(field, section);
  }

  private static Field findField(Class<?> type, String name) {
    Class<?> current = type;
    while (current != null && current != Object.class) {
      try {
        Field field = current.getDeclaredField(name);
        field.setAccessible(true);
        return field;
      } catch (NoSuchFieldException ignored) {
        current = current.getSuperclass();
      } catch (Throwable ignored) {
        return null;
      }
    }
    return null;
  }

  private static List<Field> getSerializableFields(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    collectFields(type, fields);
    return fields;
  }

  private static void collectFields(Class<?> type, List<Field> out) {
    if (type == null || type == Object.class) {
      return;
    }

    collectFields(type.getSuperclass(), out);
    for (Field field : type.getDeclaredFields()) {
      if (field.isSynthetic()) {
        continue;
      }

      int modifiers = field.getModifiers();
      if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
        continue;
      }

      try {
        field.setAccessible(true);
        out.add(field);
      } catch (Throwable ignored) {
      }
    }
  }

  private static Object instantiate(Class<?> type) {
    Class<?> normalized = normalizeType(type);
    if (normalized == null
        || normalized.isPrimitive()
        || normalized.isEnum()
        || normalized == String.class
        || isNumericType(normalized)
        || normalized == Boolean.class
        || normalized == Character.class) {
      return null;
    }

    try {
      return normalized.getDeclaredConstructor().newInstance();
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static boolean setFieldValue(Field field, Object target, Object value) {
    try {
      field.setAccessible(true);
      field.set(target, value);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static Object getFieldValue(Field field, Object target) {
    try {
      field.setAccessible(true);
      return field.get(target);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static Object coerceValue(Object value, Class<?> targetType) {
    if (value == null) {
      return null;
    }

    Class<?> normalizedTarget = normalizeType(targetType);
    if (normalizedTarget != null && normalizedTarget.isAssignableFrom(value.getClass())) {
      return value;
    }

    ParseResult parsed = parseInputValue(targetType, String.valueOf(value));
    return parsed.success() ? parsed.value() : value;
  }

  private static Class<?> normalizeType(Class<?> type) {
    if (type == null || !type.isPrimitive()) {
      return type;
    }

    if (type == int.class) return Integer.class;
    if (type == long.class) return Long.class;
    if (type == double.class) return Double.class;
    if (type == float.class) return Float.class;
    if (type == short.class) return Short.class;
    if (type == byte.class) return Byte.class;
    if (type == boolean.class) return Boolean.class;
    if (type == char.class) return Character.class;
    return type;
  }

  private static boolean isNumericType(Class<?> type) {
    return type == Integer.class
        || type == Long.class
        || type == Double.class
        || type == Float.class
        || type == Short.class
        || type == Byte.class;
  }

  private static boolean isSectionType(Class<?> type) {
    Class<?> normalized = normalizeType(type);
    if (normalized == null) {
      return false;
    }

    if (normalized.isPrimitive() || normalized.isEnum()) {
      return false;
    }
    if (normalized == String.class || normalized == Character.class || normalized == Boolean.class || isNumericType(normalized)) {
      return false;
    }
    return !Map.class.isAssignableFrom(normalized) && !Collection.class.isAssignableFrom(normalized) && !normalized.isArray();
  }

  private static Object cycleEnum(Class<?> enumType, Object current, int direction) {
    Class<?> normalized = normalizeType(enumType);
    if (normalized == null || !normalized.isEnum()) {
      return null;
    }

    Object[] constants = normalized.getEnumConstants();
    if (constants == null || constants.length == 0) {
      return null;
    }

    int currentIndex = 0;
    if (current != null) {
      for (int i = 0; i < constants.length; i++) {
        if (Objects.equals(constants[i], current)) {
          currentIndex = i;
          break;
        }
      }
    }

    int nextIndex = currentIndex + direction;
    if (nextIndex < 0) {
      nextIndex = constants.length - 1;
    } else if (nextIndex >= constants.length) {
      nextIndex = 0;
    }
    return constants[nextIndex];
  }

  private static Object parseEnumConstant(Class<?> enumType, String value) {
    if (enumType == null || !enumType.isEnum() || value == null) {
      return null;
    }

    for (Object constant : enumType.getEnumConstants()) {
      if (constant != null && constant.toString().equalsIgnoreCase(value)) {
        return constant;
      }
    }
    return null;
  }

  private static String enumConstants(Class<?> enumType) {
    if (enumType == null || !enumType.isEnum()) {
      return "";
    }

    List<String> values = new ArrayList<>();
    for (Object constant : enumType.getEnumConstants()) {
      if (constant != null) {
        values.add(constant.toString());
      }
    }
    return String.join(", ", values);
  }

  private static int clampPage(int page, int pageCount) {
    if (pageCount <= 0) {
      return 0;
    }
    return Math.max(0, Math.min(page, pageCount - 1));
  }

  private static PageLayout pageLayout(int totalEntries, boolean includeBack) {
    int safeEntries = Math.max(0, totalEntries);

    if (!includeBack) {
      int rows = Math.max(MIN_CONTENT_ROWS, Math.min(MAX_CONTENT_ROWS + 1,
          (int) Math.ceil(Math.max(1, safeEntries) / 9D)));
      int itemsPerPage = rows * 9;
      if (safeEntries <= itemsPerPage) {
        return new PageLayout(rows, rows, itemsPerPage, 1, false, false, -1);
      }

      int contentRows = MAX_CONTENT_ROWS;
      rows = contentRows + 1;
      itemsPerPage = contentRows * 9;
      int pageCount = Math.max(1, (int) Math.ceil(safeEntries / (double) itemsPerPage));
      return new PageLayout(rows, contentRows, itemsPerPage, pageCount, true, false, rows - 1);
    }

    int contentRows = Math.max(MIN_CONTENT_ROWS, Math.min(MAX_CONTENT_ROWS,
        (int) Math.ceil(Math.max(1, safeEntries) / 9D)));
    int rows = contentRows + 1;
    int itemsPerPage = contentRows * 9;
    if (safeEntries <= itemsPerPage) {
      return new PageLayout(rows, contentRows, itemsPerPage, 1, false, true, rows - 1);
    }

    contentRows = MAX_CONTENT_ROWS;
    rows = contentRows + 1;
    itemsPerPage = contentRows * 9;
    int pageCount = Math.max(1, (int) Math.ceil(safeEntries / (double) itemsPerPage));
    return new PageLayout(rows, contentRows, itemsPerPage, pageCount, true, true, rows - 1);
  }

  static int centeredPosition(int index, int rowCount) {
    int safeCount = Math.max(1, Math.min(9, rowCount));
    int safeIndex = Math.max(0, Math.min(index, safeCount - 1));
    int start = -(safeCount / 2);
    if ((safeCount & 1) == 1) {
      start = -((safeCount - 1) / 2);
    }
    return start + safeIndex;
  }

  private static String shortenTitle(String title) {
    String safe = title == null ? ReactLanguage.plain(GuiMessages.CONFIG_FALLBACK_TITLE) : title;
    if (safe.length() <= 32) {
      return safe;
    }
    return safe.substring(0, 29) + "...";
  }

  static String displayName(String key) {
    if (key == null || key.isBlank()) {
      return ReactLanguage.plain(GuiMessages.CONFIG_UNNAMED);
    }

    String spaced = key
        .replace('_', ' ')
        .replace('-', ' ')
        .replaceAll("([a-z])([A-Z])", "$1 $2")
        .trim();
    if (spaced.isBlank()) {
      return key;
    }
    return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
  }

  private boolean isLanguageEntry(FieldEntry entry) {
    Object main = source.mainSettings();
    return main != null && entry.field().getDeclaringClass() == main.getClass() && entry.field().getName().equals("language");
  }

  static String summarizeValue(Object value) {
    if (value == null) {
      return ReactLanguage.plain(GuiMessages.NULL_VALUE);
    }

    if (value instanceof Map<?, ?> map) {
      return ReactLanguage.plain(GuiMessages.CONFIG_VALUE_MAP, MessageArgument.untrusted("count", map.size()));
    }
    if (value instanceof Collection<?> collection) {
      return ReactLanguage.plain(GuiMessages.CONFIG_VALUE_LIST, MessageArgument.untrusted("count", collection.size()));
    }
    if (value.getClass().isArray()) {
      return ReactLanguage.plain(GuiMessages.CONFIG_VALUE_ARRAY, MessageArgument.untrusted("count", Array.getLength(value)));
    }
    String text = String.valueOf(value).replace("\n", "\\n").replace("\r", "\\r");
    if (text.length() > MAX_VALUE_PREVIEW) {
      return text.substring(0, MAX_VALUE_PREVIEW - 3) + "...";
    }
    return text;
  }

  private static String configurationStatus(boolean active) {
    return ReactLanguage.plain(active ? GuiMessages.CONFIG_STATUS_ACTIVE : GuiMessages.CONFIG_STATUS_INACTIVE);
  }

  private static String normalizeSortKey(String input) {
    return C.stripColor(input == null ? "" : input).toLowerCase(Locale.ROOT).replace(" ", "");
  }

  private static Comparator<SectionIndexEntry> groupedIndexComparator(String rootPrefix) {
    return Comparator
        .comparingInt((SectionIndexEntry entry) -> ReactGuiTaxonomy.groupOrder(indexEntryId(entry.path(), rootPrefix)))
        .thenComparing(entry -> normalizeSortKey(entry.displayName()));
  }

  private static String indexEntryId(String path, String rootPrefix) {
    String payload = stripPrefix(path, rootPrefix);
    if (payload.isBlank()) {
      return "";
    }

    String[] parts = payload.split("\\Q.\\E", 2);
    return parts.length == 0 ? "" : parts[0];
  }

  private enum ElementKind {
    BOOLEAN,
    NUMBER,
    STRING,
    ENUM,
    SECTION,
    MAP,
    LIST,
    UNSUPPORTED
  }

  private record PageLayout(
      int rows,
      int contentRows,
      int itemsPerPage,
      int pageCount,
      boolean pagination,
      boolean includeBack,
      int controlRow
  ) {
  }

  private record SectionTarget(String sourceTag, Object sectionObject) {
  }

  private record EditTarget(String sourceTag, Object rootObject, String objectPath, File file, BooleanSupplier reload) {
  }

  private record FieldEntry(Field field, String path, Object value, ElementKind kind) {
  }

  private record SectionIndexEntry(
      String path,
      String displayName,
      String icon,
      String fallbackIcon,
      String lore,
      boolean highlighted,
      String quickTogglePath,
      Boolean quickToggleValue
  ) {
    private SectionIndexEntry(String path, String displayName, String icon, String lore) {
      this(path, displayName, icon, CHEST, lore, false, null, null);
    }

    private SectionIndexEntry(String path, String displayName, String icon, String lore, boolean highlighted) {
      this(path, displayName, icon, CHEST, lore, highlighted, null, null);
    }
  }

  public record ParseResult(boolean success, Object value, String error) {
    public static ParseResult ok(Object value) {
      return new ParseResult(true, value, "");
    }

    public static ParseResult fail(String error) {
      return new ParseResult(
          false,
          null,
          error == null ? ReactLanguage.plain(GuiMessages.INPUT_INVALID_VALUE) : error
      );
    }
  }
}
