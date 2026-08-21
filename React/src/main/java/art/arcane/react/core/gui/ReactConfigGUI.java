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

package art.arcane.react.core.gui;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.ConfigInputController;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.HotloadController;
import art.arcane.react.core.controller.PlayerController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.TweakController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.format.C;
import art.arcane.react.util.inventorygui.UIStaticDecorator;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.config.ConfigFileSupport;
import art.arcane.react.util.project.config.ConfigDocumentation;
import art.arcane.react.util.project.config.TomlCodec;
import art.arcane.react.util.project.registry.Registry;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.WindowResolution;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class ReactConfigGUI {
  private static final String ROOT_MAIN = "main";
  private static final String ROOT_CORE = "core";
  private static final String ROOT_FEATURE = "feature";
  private static final String ROOT_TWEAK = "tweak";
  private static final String ROOT_ACTION = "action";
  private static final String ROOT_SAMPLER = "sampler";
  private static final String ROOT_ADVANCED = "advanced";
  private static final String ROOT_CATEGORY = "cat";

  private static final String[] PRESET_MANAGED_IDS = {
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

  private static final int MIN_CONTENT_ROWS = 1;
  private static final int MAX_CONTENT_ROWS = 5;
  private static final int PAGE_JUMP = 5;
  private static final int MAX_VALUE_PREVIEW = 64;
  private static final Object WRITE_LOCK = new Object();
  private static final Map<UUID, UIWindow> ACTIVE_WINDOWS = new ConcurrentHashMap<>();

  private ReactConfigGUI() {
  }

  public static boolean canConfigure(Player player) {
    return player != null && (player.isOp() || player.hasPermission("react.configurator"));
  }

  public static void open(Player player) {
    open(player, "", 0);
  }

  public static void open(Player player, String sectionPath) {
    open(player, sectionPath, 0);
  }

  public static void open(Player player, String sectionPath, int page) {
    if (player == null) {
      return;
    }

    if (!canConfigure(player)) {
      ReactLanguage.send(player, GuiMessages.CONFIG_PERMISSION);
      return;
    }

    if (!Bukkit.isPrimaryThread()) {
      String path = sectionPath;
      int targetPage = page;
      J.runEntity(player, () -> open(player, path, targetPage));
      return;
    }

    playPageTurn(player);
    String safePath = normalizePath(sectionPath);
    if (safePath.isBlank()) {
      openRoot(player, page);
      return;
    }

    if (safePath.equals(ROOT_MAIN)) {
      openMain(player, page);
      return;
    }

    if (safePath.equals(ROOT_CORE)) {
      openCoreIndex(player, page);
      return;
    }

    if (safePath.equals(ROOT_FEATURE)) {
      openFeatureIndex(player, page);
      return;
    }

    if (safePath.equals(ROOT_TWEAK)) {
      openTweakIndex(player, page);
      return;
    }

    if (safePath.equals(ROOT_ACTION)) {
      openActionIndex(player, page);
      return;
    }

    if (safePath.equals(ROOT_SAMPLER)) {
      openSamplerIndex(player, page);
      return;
    }

    if (safePath.equals(ROOT_ADVANCED)) {
      openAdvanced(player, page);
      return;
    }

    if (safePath.equals(ROOT_CATEGORY)) {
      openRoot(player, page);
      return;
    }

    if (safePath.startsWith(ROOT_CATEGORY + ".")) {
      openCategoryIndex(player, safePath.substring(ROOT_CATEGORY.length() + 1), page);
      return;
    }

    SectionTarget target = resolveSectionTarget(safePath, false);
    if (target == null || target.sectionObject() == null) {
      ReactLanguage.send(player, GuiMessages.CONFIG_OPEN_FAILED, MessageArgument.untrusted("path", safePath));
      return;
    }

    List<FieldEntry> entries = buildEntries(safePath, target.sectionObject(), target.sourceTag());
    openFieldEntries(player, safePath, entries, page);
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

  public static void confirmAndApply(Player actor, String returnSectionPath, int returnPage, String valuePath, Object value) {
    String path = normalizePath(valuePath);
    if (path.isBlank()) {
      return;
    }

    String section = normalizePath(returnSectionPath);
    applyAndSave(actor, path, value);
    navigateTo(actor, section, Math.max(0, returnPage));
  }

  public static boolean applyAndSave(Player actor, String valuePath, Object value) {
    String path = normalizePath(valuePath);
    if (path.isBlank()) {
      return false;
    }

    synchronized (WRITE_LOCK) {
      EditTarget target = resolveEditTarget(path);
      if (target == null) {
        if (actor != null) {
          ReactLanguage.send(actor, GuiMessages.CONFIG_SET_FAILED, MessageArgument.untrusted("path", path));
        }
        return false;
      }

      Object before = readPathValue(target.rootObject(), target.objectPath());
      String beforeToml = TomlCodec.toToml(target.rootObject(), target.sourceTag());

      if (!setPathValue(target.rootObject(), target.objectPath(), value, true)) {
        if (actor != null) {
          ReactLanguage.send(actor, GuiMessages.CONFIG_SET_FAILED, MessageArgument.untrusted("path", path));
        }
        return false;
      }

      try {
        String updatedToml = TomlCodec.toToml(target.rootObject(), target.sourceTag());
        ConfigFileSupport.writeConfig(target.file(), updatedToml);
      } catch (Throwable e) {
        J.attempt(() -> ConfigFileSupport.writeConfig(target.file(), beforeToml));
        target.reload().getAsBoolean();
        if (actor != null) {
          ReactLanguage.send(actor, GuiMessages.CONFIG_PERSIST_FAILED, MessageArgument.untrusted("reason", String.valueOf(e.getMessage())));
        }
        return false;
      }

      if (!target.reload().getAsBoolean()) {
        J.attempt(() -> ConfigFileSupport.writeConfig(target.file(), beforeToml));
        target.reload().getAsBoolean();
        if (actor != null) {
          ReactLanguage.send(actor, GuiMessages.CONFIG_RELOAD_REVERTED);
        }
        return false;
      }

      target.afterReload().run();
      if (actor != null) {
        ReactLanguage.send(
            actor,
            GuiMessages.CONFIG_UPDATED,
            MessageArgument.untrusted("path", path),
            MessageArgument.untrusted("before", summarizeValue(before)),
            MessageArgument.untrusted("after", summarizeValue(value))
        );
      }
      return true;
    }
  }

  private static void openAdvanced(Player player, int page) {
    int coreCount = React.instance != null && React.instance.getControllerRegistry() != null
        ? React.instance.getControllerRegistry().size()
        : 0;
    FeatureController featureController = React.controller(FeatureController.class);
    int featureCount = featureController != null && featureController.getFeatures() != null
        ? featureController.getFeatures().size()
        : 0;
    int activeFeatureCount = featureController != null && featureController.getActiveFeatures() != null
        ? featureController.getActiveFeatures().size()
        : 0;
    TweakController tweakController = React.controller(TweakController.class);
    int tweakCount = tweakController != null && tweakController.getTweaks() != null
        ? tweakController.getTweaks().size()
        : 0;
    int activeTweakCount = tweakController != null && tweakController.getActiveTweaks() != null
        ? tweakController.getActiveTweaks().size()
        : 0;
    ActionController actionController = React.controller(ActionController.class);
    int actionCount = actionController != null && actionController.getActions() != null
        ? actionController.getActions().size()
        : 0;
    SampleController sampleController = React.controller(SampleController.class);
    int samplerCount = sampleController != null && sampleController.getSamplers() != null
        ? sampleController.getSamplers().size()
        : 0;

    List<SectionIndexEntry> entries = new ArrayList<>();
    entries.add(new SectionIndexEntry(ROOT_MAIN, ReactLanguage.plain(GuiMessages.CONFIG_MAIN), Material.COMPARATOR, ""));
    entries.add(new SectionIndexEntry(
        ROOT_CORE,
        ReactLanguage.plain(GuiMessages.CONFIG_CORE_COUNT, MessageArgument.untrusted("count", coreCount)),
        Material.REDSTONE,
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
        Material.NETHER_STAR,
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
        Material.BLAZE_POWDER,
        "",
        activeTweakCount > 0
    ));
    entries.add(new SectionIndexEntry(
        ROOT_ACTION,
        ReactLanguage.plain(GuiMessages.CONFIG_ACTION_COUNT, MessageArgument.untrusted("count", actionCount)),
        Material.ANVIL,
        "",
        actionCount > 0
    ));
    entries.add(new SectionIndexEntry(
        ROOT_SAMPLER,
        ReactLanguage.plain(GuiMessages.CONFIG_SAMPLER_COUNT, MessageArgument.untrusted("count", samplerCount)),
        Material.CLOCK,
        "",
        samplerCount > 0
    ));
    openSectionIndex(player, ROOT_ADVANCED, page, ReactLanguage.plain(GuiMessages.CONFIG_ADVANCED_TITLE), entries);
  }

  private static void openRoot(Player player, int page) {
    Map<String, int[]> counts = new HashMap<>();
    FeatureController featureController = React.controller(FeatureController.class);
    if (featureController != null && featureController.getFeatures() != null) {
      for (Feature feature : featureController.getFeatures().all()) {
        if (feature == null) {
          continue;
        }
        accumulateCategoryCount(counts, feature.getId(), feature.isEnabled());
      }
    }
    TweakController tweakController = React.controller(TweakController.class);
    if (tweakController != null && tweakController.getTweaks() != null) {
      for (Tweak tweak : tweakController.getTweaks().all()) {
        if (tweak == null) {
          continue;
        }
        accumulateCategoryCount(counts, tweak.getId(), tweak.isEnabled());
      }
    }

    UIWindow window = baseWindow(player, ReactLanguage.plain(GuiMessages.CONFIG_TITLE), 6);

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

      UIElement element = new UIElement("cfg-cat-" + category.key())
          .setMaterial(new MaterialBlock(category.icon()))
          .setName(C.WHITE + category.label());
      element.addLore(C.GRAY + category.description());
      element.addLore(C.GREEN + ReactLanguage.plain(
          GuiMessages.CONFIG_CATEGORY_ENABLED,
          MessageArgument.untrusted("enabled", enabled),
          MessageArgument.untrusted("total", total)
      ));
      if (enabled > 0) {
        element.setEnchanted(true);
      }
      element.onLeftClick((e) -> navigateTo(player, ROOT_CATEGORY + "." + category.key(), 0));
      window.setElement(w, row, element);
    }

    if (categoryRowCount == 0) {
      window.setElement(0, 0, new UIElement("cfg-empty")
          .setMaterial(new MaterialBlock(Material.PAPER))
          .setName(C.GRAY + ReactLanguage.plain(GuiMessages.CONFIG_NO_MODULES)));
    }

    int presetRow = Math.max(categoryRows, 3);

    UIElement off = new UIElement("cfg-preset-off")
        .setMaterial(new MaterialBlock(Material.GRAY_DYE))
        .setName(C.AQUA + ReactLanguage.plain(
            GuiMessages.CONFIG_PROFILE_TITLE,
            MessageArgument.untrusted("profile", ReactLanguage.plain(GuiMessages.CONFIG_PROFILE_OFF))
        ));
    off.addLore(C.GRAY + ReactLanguage.plain(GuiMessages.CONFIG_PROFILE_OFF_DESCRIPTION));
    off.onLeftClick((e) -> applyPreset(player, "off"));
    window.setElement(-3, presetRow, off);

    UIElement light = new UIElement("cfg-preset-light")
        .setMaterial(new MaterialBlock(Material.LIME_DYE))
        .setName(C.AQUA + ReactLanguage.plain(
            GuiMessages.CONFIG_PROFILE_TITLE,
            MessageArgument.untrusted("profile", ReactLanguage.plain(GuiMessages.CONFIG_PROFILE_LIGHT))
        ));
    light.addLore(C.GRAY + ReactLanguage.plain(GuiMessages.CONFIG_PROFILE_LIGHT_DESCRIPTION));
    light.onLeftClick((e) -> applyPreset(player, "light"));
    window.setElement(-1, presetRow, light);

    UIElement balanced = new UIElement("cfg-preset-balanced")
        .setMaterial(new MaterialBlock(Material.GOLD_INGOT))
        .setName(C.AQUA + ReactLanguage.plain(
            GuiMessages.CONFIG_PROFILE_TITLE,
            MessageArgument.untrusted("profile", ReactLanguage.plain(GuiMessages.CONFIG_PROFILE_BALANCED))
        ));
    balanced.addLore(C.GRAY + ReactLanguage.plain(GuiMessages.CONFIG_PROFILE_BALANCED_DESCRIPTION));
    balanced.onLeftClick((e) -> applyPreset(player, "balanced"));
    window.setElement(1, presetRow, balanced);

    UIElement high = new UIElement("cfg-preset-high")
        .setMaterial(new MaterialBlock(Material.BLAZE_POWDER))
        .setName(C.AQUA + ReactLanguage.plain(
            GuiMessages.CONFIG_PROFILE_TITLE,
            MessageArgument.untrusted("profile", ReactLanguage.plain(GuiMessages.CONFIG_PROFILE_HIGH))
        ));
    high.addLore(C.GRAY + ReactLanguage.plain(GuiMessages.CONFIG_PROFILE_HIGH_DESCRIPTION));
    high.onLeftClick((e) -> applyPreset(player, "high"));
    window.setElement(3, presetRow, high);

    UIElement main = new UIElement("cfg-root-main")
        .setMaterial(new MaterialBlock(Material.COMPARATOR))
        .setName(C.WHITE + ReactLanguage.plain(GuiMessages.CONFIG_MAIN));
    main.addLore(C.GRAY + ReactLanguage.plain(GuiMessages.CONFIG_MAIN_DESCRIPTION));
    main.onLeftClick((e) -> navigateTo(player, ROOT_MAIN, 0));
    window.setElement(-1, 5, main);

    UIElement advanced = new UIElement("cfg-root-advanced")
        .setMaterial(new MaterialBlock(Material.COMMAND_BLOCK))
        .setName(C.WHITE + ReactLanguage.plain(GuiMessages.CONFIG_ADVANCED));
    advanced.addLore(C.GRAY + ReactLanguage.plain(GuiMessages.CONFIG_ADVANCED_DESCRIPTION));
    advanced.onLeftClick((e) -> navigateTo(player, ROOT_ADVANCED, 0));
    window.setElement(1, 5, advanced);

    window.open();
    ACTIVE_WINDOWS.put(player.getUniqueId(), window);
  }

  private static void accumulateCategoryCount(Map<String, int[]> counts, String id, boolean enabled) {
    String key = ReactGuiTaxonomy.categoryForId(id).key();
    int[] tally = counts.computeIfAbsent(key, (k) -> new int[]{0, 0});
    tally[1]++;
    if (enabled) {
      tally[0]++;
    }
  }

  private static void openCategoryIndex(Player player, String categoryKey, int page) {
    String label = categoryKey;
    for (ReactGuiTaxonomy.Category category : ReactGuiTaxonomy.topLevelCategories()) {
      if (category.key().equals(categoryKey)) {
        label = category.label();
        break;
      }
    }

    List<SectionIndexEntry> entries = new ArrayList<>();
    FeatureController featureController = React.controller(FeatureController.class);
    Registry<Feature> featureRegistry = featureController == null ? null : featureController.getFeatures();
    if (featureRegistry != null) {
      for (Feature feature : featureRegistry.all()) {
        if (feature == null || !ReactGuiTaxonomy.categoryForId(feature.getId()).key().equals(categoryKey)) {
          continue;
        }
        boolean active = featureController.getActiveFeatures() != null && featureController.getActiveFeatures().containsKey(feature.getId());
        entries.add(new SectionIndexEntry(
            ROOT_FEATURE + "." + feature.getId(),
            C.stripColor(feature.getName()),
            ReactGuiTaxonomy.iconForId(feature.getId()),
            ReactLanguage.plain(
                GuiMessages.CONFIG_LORE_STATUS_PATH,
                MessageArgument.untrusted("status", configurationStatus(active)),
                MessageArgument.untrusted("path", "feature/" + feature.getId() + ".toml")
            ),
            active,
            ROOT_FEATURE + "." + feature.getId() + ".enabled",
            feature.isEnabled()
        ));
      }
    }

    TweakController tweakController = React.controller(TweakController.class);
    Registry<Tweak> tweakRegistry = tweakController == null ? null : tweakController.getTweaks();
    if (tweakRegistry != null) {
      for (Tweak tweak : tweakRegistry.all()) {
        if (tweak == null || !ReactGuiTaxonomy.categoryForId(tweak.getId()).key().equals(categoryKey)) {
          continue;
        }
        boolean active = tweakController.getActiveTweaks() != null && tweakController.getActiveTweaks().containsKey(tweak.getId());
        entries.add(new SectionIndexEntry(
            ROOT_TWEAK + "." + tweak.getId(),
            C.stripColor(tweak.getName()),
            ReactGuiTaxonomy.iconForId(tweak.getId()),
            ReactLanguage.plain(
                GuiMessages.CONFIG_LORE_STATUS_PATH,
                MessageArgument.untrusted("status", configurationStatus(active)),
                MessageArgument.untrusted("path", "tweak/" + tweak.getId() + ".toml")
            ),
            active,
            ROOT_TWEAK + "." + tweak.getId() + ".enabled",
            tweak.isEnabled()
        ));
      }
    }

    entries.sort(Comparator.comparing(e -> normalizeSortKey(e.displayName())));
    openSectionIndex(
        player,
        ROOT_CATEGORY + "." + categoryKey,
        page,
        ReactLanguage.plain(GuiMessages.CONFIG_SECTION_TITLE, MessageArgument.untrusted("section", label)),
        entries
    );
  }

  public static int applyPresetHeadless(String preset) {
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

  private static void applyPreset(Player player, String preset) {
    int updated = applyPresetHeadless(preset);
    if (updated < 0) {
      ReactLanguage.send(player, GuiMessages.CONFIG_PROFILE_UNKNOWN, MessageArgument.untrusted("profile", String.valueOf(preset)));
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

    ReactLanguage.send(
        player,
        GuiMessages.CONFIG_PROFILE_APPLIED,
        MessageArgument.untrusted("profile", presetName),
        MessageArgument.untrusted("count", updated)
    );
    openRoot(player, 0);
  }

  private static int applyViewDistanceProfile(String preset) {
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

  private static String managedEnabledPath(String id) {
    if (id == null || id.isBlank()) {
      return null;
    }

    FeatureController featureController = React.controller(FeatureController.class);
    if (featureController != null && featureController.getFeatures() != null && featureController.getFeatures().get(id) != null) {
      return ROOT_FEATURE + "." + id + ".enabled";
    }

    TweakController tweakController = React.controller(TweakController.class);
    if (tweakController != null && tweakController.getTweaks() != null && tweakController.getTweaks().get(id) != null) {
      return ROOT_TWEAK + "." + id + ".enabled";
    }

    return null;
  }

  private static void openMain(Player player, int page) {
    SectionTarget target = resolveSectionTarget(ROOT_MAIN, false);
    if (target == null || target.sectionObject() == null) {
      ReactLanguage.send(player, GuiMessages.CONFIG_MAIN_UNAVAILABLE);
      return;
    }
    openFieldEntries(player, ROOT_MAIN, buildEntries(ROOT_MAIN, target.sectionObject(), target.sourceTag()), page);
  }

  private static void openCoreIndex(Player player, int page) {
    List<SectionIndexEntry> entries = new ArrayList<>();
    if (React.instance != null && React.instance.getControllerRegistry() != null) {
      for (IController controller : React.instance.getControllerRegistry().all()) {
        if (controller == null) {
          continue;
        }
        entries.add(new SectionIndexEntry(
            ROOT_CORE + "." + controller.getId(),
            displayName(controller.getName()),
            controller.getIcon(),
            ReactLanguage.plain(
                GuiMessages.CONFIG_LORE_CONFIGURE_PATH,
                MessageArgument.untrusted("path", "core/" + controller.getId() + ".toml")
            )
        ));
      }
    }

    entries.sort(Comparator.comparing(e -> normalizeSortKey(e.displayName())));
    openSectionIndex(
        player,
        ROOT_CORE,
        page,
        ReactLanguage.plain(
            GuiMessages.CONFIG_SECTION_TITLE,
            MessageArgument.untrusted("section", ReactLanguage.plain(GuiMessages.CONFIG_CORE))
        ),
        entries
    );
  }

  private static void openFeatureIndex(Player player, int page) {
    List<SectionIndexEntry> entries = new ArrayList<>();
    FeatureController controller = React.controller(FeatureController.class);
    Registry<Feature> registry = controller == null ? null : controller.getFeatures();
    if (registry != null) {
      for (Feature feature : registry.all()) {
        if (feature == null) {
          continue;
        }
        boolean active = controller.getActiveFeatures() != null && controller.getActiveFeatures().containsKey(feature.getId());
        entries.add(new SectionIndexEntry(
            ROOT_FEATURE + "." + feature.getId(),
            C.stripColor(feature.getName()),
            ReactGuiTaxonomy.iconForId(feature.getId()),
            ReactLanguage.plain(
                GuiMessages.CONFIG_LORE_STATUS_GROUP_PATH,
                MessageArgument.untrusted("status", configurationStatus(active)),
                MessageArgument.untrusted("group", ReactGuiTaxonomy.groupLabel(feature.getId())),
                MessageArgument.untrusted("path", "feature/" + feature.getId() + ".toml")
            ),
            active,
            ROOT_FEATURE + "." + feature.getId() + ".enabled",
            feature.isEnabled()
        ));
      }
    }

    entries.sort(groupedIndexComparator(ROOT_FEATURE));
    openSectionIndex(
        player,
        ROOT_FEATURE,
        page,
        ReactLanguage.plain(
            GuiMessages.CONFIG_SECTION_TITLE,
            MessageArgument.untrusted("section", ReactLanguage.plain(GuiMessages.CONFIG_FEATURES))
        ),
        entries
    );
  }

  private static void openTweakIndex(Player player, int page) {
    List<SectionIndexEntry> entries = new ArrayList<>();
    TweakController controller = React.controller(TweakController.class);
    Registry<Tweak> registry = controller == null ? null : controller.getTweaks();
    if (registry != null) {
      for (Tweak tweak : registry.all()) {
        if (tweak == null) {
          continue;
        }
        boolean active = controller.getActiveTweaks() != null && controller.getActiveTweaks().containsKey(tweak.getId());
        entries.add(new SectionIndexEntry(
            ROOT_TWEAK + "." + tweak.getId(),
            C.stripColor(tweak.getName()),
            ReactGuiTaxonomy.iconForId(tweak.getId()),
            ReactLanguage.plain(
                GuiMessages.CONFIG_LORE_STATUS_GROUP_PATH,
                MessageArgument.untrusted("status", configurationStatus(active)),
                MessageArgument.untrusted("group", ReactGuiTaxonomy.groupLabel(tweak.getId())),
                MessageArgument.untrusted("path", "tweak/" + tweak.getId() + ".toml")
            ),
            active,
            ROOT_TWEAK + "." + tweak.getId() + ".enabled",
            tweak.isEnabled()
        ));
      }
    }

    entries.sort(groupedIndexComparator(ROOT_TWEAK));
    openSectionIndex(
        player,
        ROOT_TWEAK,
        page,
        ReactLanguage.plain(
            GuiMessages.CONFIG_SECTION_TITLE,
            MessageArgument.untrusted("section", ReactLanguage.plain(GuiMessages.CONFIG_TWEAKS))
        ),
        entries
    );
  }

  private static void openActionIndex(Player player, int page) {
    List<SectionIndexEntry> entries = new ArrayList<>();
    ActionController controller = React.controller(ActionController.class);
    Registry<Action<?>> registry = controller == null ? null : controller.getActions();
    if (registry != null) {
      for (Action<?> action : registry.all()) {
        if (action == null) {
          continue;
        }
        entries.add(new SectionIndexEntry(
            ROOT_ACTION + "." + action.getId(),
            C.stripColor(action.getName()),
            ReactGuiTaxonomy.iconForId(action.getId()),
            ReactLanguage.plain(
                GuiMessages.CONFIG_LORE_GROUP_PATH,
                MessageArgument.untrusted("group", ReactGuiTaxonomy.groupLabel(action.getId())),
                MessageArgument.untrusted("path", "action/" + action.getId() + ".toml")
            )
        ));
      }
    }

    entries.sort(groupedIndexComparator(ROOT_ACTION));
    openSectionIndex(
        player,
        ROOT_ACTION,
        page,
        ReactLanguage.plain(
            GuiMessages.CONFIG_SECTION_TITLE,
            MessageArgument.untrusted("section", ReactLanguage.plain(GuiMessages.CONFIG_ACTIONS))
        ),
        entries
    );
  }

  private static void openSamplerIndex(Player player, int page) {
    List<SectionIndexEntry> entries = new ArrayList<>();
    SampleController controller = React.controller(SampleController.class);
    Registry<Sampler> registry = controller == null ? null : controller.getSamplers();
    if (registry != null) {
      for (Sampler sampler : registry.all()) {
        if (sampler == null) {
          continue;
        }
        entries.add(new SectionIndexEntry(
            ROOT_SAMPLER + "." + sampler.getId(),
            C.stripColor(sampler.getName()),
            ReactGuiTaxonomy.iconForId(sampler.getId()),
            ReactLanguage.plain(
                GuiMessages.CONFIG_LORE_GROUP_PATH,
                MessageArgument.untrusted("group", ReactGuiTaxonomy.groupLabel(sampler.getId())),
                MessageArgument.untrusted("path", "sampler/" + sampler.getId() + ".toml")
            )
        ));
      }
    }

    entries.sort(groupedIndexComparator(ROOT_SAMPLER));
    openSectionIndex(
        player,
        ROOT_SAMPLER,
        page,
        ReactLanguage.plain(
            GuiMessages.CONFIG_SECTION_TITLE,
            MessageArgument.untrusted("section", ReactLanguage.plain(GuiMessages.CONFIG_SAMPLERS))
        ),
        entries
    );
  }

  private static void openSectionIndex(Player player, String path, int page, String title, List<SectionIndexEntry> entries) {
    String safePath = normalizePath(path);
    boolean includeBack = !safePath.isBlank();
    PageLayout layout = pageLayout(entries.size(), includeBack);
    int pageCount = layout.pageCount();
    int currentPage = clampPage(page, pageCount);
    int start = currentPage * layout.itemsPerPage();
    int end = Math.min(entries.size(), start + layout.itemsPerPage());

    UIWindow window = baseWindow(player, title, layout.rows());
    if (entries.isEmpty()) {
      window.setElement(0, 0, new UIElement("cfg-empty")
          .setMaterial(new MaterialBlock(Material.PAPER))
          .setName(C.GRAY + ReactLanguage.plain(GuiMessages.CONFIG_NO_ENTRIES)));
    } else {
      for (int row = 0; row < layout.contentRows(); row++) {
        int rowStart = start + (row * 9);
        if (rowStart >= end) {
          break;
        }

        int rowCount = Math.min(9, end - rowStart);
        for (int i = 0; i < rowCount; i++) {
          SectionIndexEntry entry = entries.get(rowStart + i);
          int h = row;
          int w = centeredPosition(i, rowCount);
          String displayName = C.WHITE + entry.displayName();
          if (entry.quickTogglePath() != null && entry.quickToggleValue() != null) {
            String status = entry.quickToggleValue()
                ? C.GREEN + ReactLanguage.plain(GuiMessages.CONFIG_STATUS_ON)
                : C.RED + ReactLanguage.plain(GuiMessages.CONFIG_STATUS_OFF);
            displayName += C.GRAY + " [" + status + C.GRAY + "]";
          }

          UIElement element = new UIElement("cfg-index-" + entry.path())
              .setMaterial(new MaterialBlock(entry.material()))
              .setName(displayName);
          if (entry.lore() != null && !entry.lore().isBlank()) {
            element.addLore(C.GRAY + entry.lore());
          }
          element.addLore(C.DARK_GRAY + ReactLanguage.plain(GuiMessages.CONFIG_PATH, MessageArgument.untrusted("path", entry.path())));
          element.onLeftClick((e) -> navigateTo(player, entry.path(), 0));
          if (entry.highlighted()) {
            element.setEnchanted(true);
          }
          if (entry.quickTogglePath() != null && entry.quickToggleValue() != null) {
            element.addLore(C.GREEN + ReactLanguage.plain(GuiMessages.CONFIG_TOGGLE_ENABLED));
            element.onRightClick((e) -> confirmAndApply(
                player,
                safePath,
                currentPage,
                entry.quickTogglePath(),
                !entry.quickToggleValue()
            ));
          }
          window.setElement(w, h, element);
        }
      }
    }

    applyControls(window, player, safePath, currentPage, layout, entries.size(), start, end);
    window.open();
    ACTIVE_WINDOWS.put(player.getUniqueId(), window);
  }

  private static void openFieldEntries(Player player, String sectionPath, List<FieldEntry> entries, int page) {
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

    UIWindow window = baseWindow(player, title, layout.rows());
    if (entries.isEmpty()) {
      window.setElement(0, 0, new UIElement("cfg-empty")
          .setMaterial(new MaterialBlock(Material.PAPER))
          .setName(C.GRAY + ReactLanguage.plain(GuiMessages.CONFIG_NO_SETTINGS)));
    } else {
      for (int row = 0; row < layout.contentRows(); row++) {
        int rowStart = start + (row * 9);
        if (rowStart >= end) {
          break;
        }

        int rowCount = Math.min(9, end - rowStart);
        for (int i = 0; i < rowCount; i++) {
          FieldEntry entry = entries.get(rowStart + i);
          int h = row;
          int w = centeredPosition(i, rowCount);
          window.setElement(w, h, createElementForEntry(player, safePath, currentPage, entry));
        }
      }
    }

    applyControls(window, player, safePath, currentPage, layout, entries.size(), start, end);
    window.open();
    ACTIVE_WINDOWS.put(player.getUniqueId(), window);
  }

  private static UIWindow baseWindow(Player player, String title, int rows) {
    UIWindow window = ACTIVE_WINDOWS.computeIfAbsent(player.getUniqueId(), (id) -> new UIWindow(React.instance, player));
    window.onClosed((w) -> ACTIVE_WINDOWS.remove(player.getUniqueId(), window));

    if (window.getResolution() != WindowResolution.W9_H6) {
      window.setResolution(WindowResolution.W9_H6);
    }

    if (window.getViewportHeight() != rows) {
      window.setViewportHeight(rows);
    }

    String nextTitle = shortenTitle(title);
    if (!Objects.equals(window.getTitle(), nextTitle)) {
      window.setTitle(nextTitle);
    }

    window.setDecorator(new UIStaticDecorator(new UIElement("bg").setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE))));
    window.clearElements();
    return window;
  }

  private static void applyControls(
      UIWindow window,
      Player player,
      String safePath,
      int currentPage,
      PageLayout layout,
      int totalEntries,
      int start,
      int end
  ) {
    int navRow = layout.controlRow();
    if (layout.includeBack()) {
      window.setElement(4, navRow, new UIElement("cfg-back")
          .setMaterial(new MaterialBlock(Material.ARROW))
          .setName(C.GRAY + ReactLanguage.plain(GuiMessages.BACK))
          .onLeftClick((e) -> navigateTo(player, parentPath(safePath), 0)));
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
      window.setElement(prevPos, navRow, new UIElement("cfg-prev")
          .setMaterial(new MaterialBlock(Material.ARROW))
          .setName(C.WHITE + ReactLanguage.plain(GuiMessages.PREVIOUS))
          .onLeftClick((e) -> navigateTo(player, safePath, currentPage - 1))
          .onRightClick((e) -> navigateTo(player, safePath, jumpBack)));
    }

    if (currentPage < pageCount - 1) {
      window.setElement(nextPos, navRow, new UIElement("cfg-next")
          .setMaterial(new MaterialBlock(Material.ARROW))
          .setName(C.WHITE + ReactLanguage.plain(GuiMessages.NEXT))
          .onLeftClick((e) -> navigateTo(player, safePath, currentPage + 1))
          .onRightClick((e) -> navigateTo(player, safePath, jumpForward)));
    }

    int from = totalEntries <= 0 ? 0 : (start + 1);
    int to = totalEntries <= 0 ? 0 : end;
    window.setElement(infoPos, navRow, new UIElement("cfg-page-info")
        .setMaterial(new MaterialBlock(Material.PAPER))
        .setName(C.AQUA + ReactLanguage.plain(
            GuiMessages.PAGE,
            MessageArgument.untrusted("current", currentPage + 1),
            MessageArgument.untrusted("total", pageCount),
            MessageArgument.untrusted("from", from),
            MessageArgument.untrusted("to", to)
        )));
  }

  private static UIElement createElementForEntry(Player player, String sectionPath, int currentPage, FieldEntry entry) {
    Material material = materialFor(entry);
    String typePrefix = switch (entry.descriptor().kind()) {
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
    String value = switch (entry.descriptor().kind()) {
      case BOOLEAN ->
          Boolean.TRUE.equals(entry.value())
              ? ReactLanguage.plain(GuiMessages.CONFIG_VALUE_ENABLED)
              : ReactLanguage.plain(GuiMessages.CONFIG_VALUE_DISABLED);
      case SECTION -> "";
      default -> summarizeValue(entry.value());
    };
    UIElement element = new UIElement("cfg-" + entry.path())
        .setMaterial(new MaterialBlock(material))
        .setName(typePrefix + C.WHITE + name + (value.isBlank() ? "" : C.GRAY + " = " + C.AQUA + value));
    if (entry.descriptor().kind() == ElementKind.BOOLEAN && Boolean.TRUE.equals(entry.value())) {
      element.setEnchanted(true);
    }

    switch (entry.descriptor().kind()) {
      case BOOLEAN -> {
        element.onLeftClick((e) -> {
          boolean toggled = !Boolean.TRUE.equals(entry.value());
          confirmAndApply(player, sectionPath, currentPage, entry.path(), toggled);
        });
      }
      case ENUM -> {
        element.onLeftClick((e) -> {
          Object next = cycleEnum(entry.field().getType(), entry.value(), 1);
          if (next != null) {
            confirmAndApply(player, sectionPath, currentPage, entry.path(), next);
          }
        });
        element.onRightClick((e) -> {
          Object previous = cycleEnum(entry.field().getType(), entry.value(), -1);
          if (previous != null) {
            confirmAndApply(player, sectionPath, currentPage, entry.path(), previous);
          }
        });
      }
      case NUMBER, STRING -> {
        element.onLeftClick((e) -> {
          ConfigInputController inputController = React.controller(ConfigInputController.class);
          if (inputController == null) {
            ReactLanguage.send(player, GuiMessages.CONFIG_INPUT_UNAVAILABLE);
            return;
          }

          inputController.beginSession(
              player,
              entry.path(),
              sectionPath,
              currentPage,
              entry.field().getType(),
              displayName(entry.field().getName())
          );
        });
      }
      case SECTION -> {
        if (entry.value() != null) {
          int nested = getSerializableFields(entry.value().getClass()).size();
          element.setName(typePrefix + C.WHITE + name + C.GRAY + " (" + C.AQUA + nested + C.GRAY + ")");
        } else {
          element.setName(typePrefix + C.WHITE + name + C.GRAY + " (" + C.RED + ReactLanguage.plain(GuiMessages.NULL_VALUE) + C.GRAY + ")");
        }
        element.onLeftClick((e) -> {
          if (entry.value() == null) {
            ReactLanguage.send(player, GuiMessages.CONFIG_SECTION_NULL);
            return;
          }
          navigateTo(player, entry.path(), 0);
        });
      }
      case MAP, LIST, UNSUPPORTED -> {
      }
    }

    return element;
  }

  private static Material materialFor(FieldEntry entry) {
    return switch (entry.descriptor().kind()) {
      case BOOLEAN ->
          Boolean.TRUE.equals(entry.value()) ? Material.LIME_DYE : Material.GRAY_DYE;
      case NUMBER -> Material.CLOCK;
      case STRING -> Material.NAME_TAG;
      case ENUM -> Material.BOOK;
      case SECTION -> materialForSection(entry.field().getName());
      case MAP -> Material.CHEST_MINECART;
      case LIST -> Material.BARREL;
      case UNSUPPORTED -> Material.BARRIER;
    };
  }

  private static Material materialForSection(String name) {
    String key = normalizeSortKey(name);
    if (key.contains("monitor") || key.contains("hud") || key.contains("display")) {
      return Material.COMPASS;
    }
    if (key.contains("debug") || key.contains("verbose")) {
      return Material.SPYGLASS;
    }
    if (key.contains("thread") || key.contains("tick") || key.contains("performance")) {
      return Material.REDSTONE;
    }
    if (key.contains("entity") || key.contains("mob")) {
      return Material.ZOMBIE_HEAD;
    }
    if (key.contains("chunk") || key.contains("world")) {
      return Material.GRASS_BLOCK;
    }
    if (key.contains("memory") || key.contains("gc")) {
      return Material.SLIME_BLOCK;
    }
    return Material.CHEST;
  }

  private static List<FieldEntry> buildEntries(String sectionPath, Object sectionObject, String sourceTag) {
    List<FieldEntry> sections = new ArrayList<>();
    List<FieldEntry> values = new ArrayList<>();
    for (Field field : getSerializableFields(sectionObject.getClass())) {
      Object value = getFieldValue(field, sectionObject);
      if (!ConfigDocumentation.shouldExposeField(sourceTag, sectionPath, field, value)) {
        continue;
      }

      String childPath = joinPath(sectionPath, field.getName());
      ElementDescriptor descriptor = describe(field, value);
      List<String> docs = ConfigDocumentation.buildFieldComments(sourceTag, childPath, field, value);
      FieldEntry entry = new FieldEntry(field, childPath, value, descriptor, docs);
      if (descriptor.kind() == ElementKind.SECTION) {
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

  private static ElementDescriptor describe(Field field, Object value) {
    Class<?> type = normalizeType(field.getType());
    if (type == Boolean.class) {
      return new ElementDescriptor(ElementKind.BOOLEAN, true);
    }
    if (isNumericType(type)) {
      return new ElementDescriptor(ElementKind.NUMBER, true);
    }
    if (type == String.class || type == Character.class) {
      return new ElementDescriptor(ElementKind.STRING, true);
    }
    if (type.isEnum()) {
      return new ElementDescriptor(ElementKind.ENUM, true);
    }
    if (Map.class.isAssignableFrom(type)) {
      return new ElementDescriptor(ElementKind.MAP, false);
    }
    if (Collection.class.isAssignableFrom(type) || type.isArray()) {
      return new ElementDescriptor(ElementKind.LIST, false);
    }
    if (value != null || isSectionType(type)) {
      return new ElementDescriptor(ElementKind.SECTION, false);
    }

    return new ElementDescriptor(ElementKind.UNSUPPORTED, false);
  }

  private static SectionTarget resolveSectionTarget(String path, boolean createMissing) {
    String normalized = normalizePath(path);
    if (normalized.isBlank()) {
      return null;
    }

    if (normalized.equals(ROOT_MAIN) || normalized.startsWith(ROOT_MAIN + ".")) {
      String objectPath = stripPrefix(normalized, ROOT_MAIN);
      return new SectionTarget("main-config", resolveSectionObject(ReactConfiguration.get(), objectPath, createMissing));
    }

    if (normalized.equals(ROOT_CORE) || normalized.startsWith(ROOT_CORE + ".")) {
      String payload = stripPrefix(normalized, ROOT_CORE);
      if (payload.isBlank()) {
        return null;
      }

      String[] parts = payload.split("\\Q.\\E", 2);
      IController controller = resolveController(parts[0]);
      if (controller == null) {
        return null;
      }

      String objectPath = parts.length > 1 ? parts[1] : "";
      return new SectionTarget("core:" + parts[0], resolveSectionObject(controller, objectPath, createMissing));
    }

    if (normalized.equals(ROOT_FEATURE) || normalized.startsWith(ROOT_FEATURE + ".")) {
      String payload = stripPrefix(normalized, ROOT_FEATURE);
      if (payload.isBlank()) {
        return null;
      }

      String[] parts = payload.split("\\Q.\\E", 2);
      Feature feature = resolveFeature(parts[0]);
      if (feature == null) {
        return null;
      }

      String objectPath = parts.length > 1 ? parts[1] : "";
      return new SectionTarget("feature:" + parts[0], resolveSectionObject(feature, objectPath, createMissing));
    }

    if (normalized.equals(ROOT_TWEAK) || normalized.startsWith(ROOT_TWEAK + ".")) {
      String payload = stripPrefix(normalized, ROOT_TWEAK);
      if (payload.isBlank()) {
        return null;
      }

      String[] parts = payload.split("\\Q.\\E", 2);
      Tweak tweak = resolveTweak(parts[0]);
      if (tweak == null) {
        return null;
      }

      String objectPath = parts.length > 1 ? parts[1] : "";
      return new SectionTarget("tweak:" + parts[0], resolveSectionObject(tweak, objectPath, createMissing));
    }

    if (normalized.equals(ROOT_ACTION) || normalized.startsWith(ROOT_ACTION + ".")) {
      String payload = stripPrefix(normalized, ROOT_ACTION);
      if (payload.isBlank()) {
        return null;
      }

      String[] parts = payload.split("\\Q.\\E", 2);
      Action<?> action = resolveAction(parts[0]);
      if (action == null) {
        return null;
      }

      String objectPath = parts.length > 1 ? parts[1] : "";
      return new SectionTarget("action:" + parts[0], resolveSectionObject(action, objectPath, createMissing));
    }

    if (normalized.equals(ROOT_SAMPLER) || normalized.startsWith(ROOT_SAMPLER + ".")) {
      String payload = stripPrefix(normalized, ROOT_SAMPLER);
      if (payload.isBlank()) {
        return null;
      }

      String[] parts = payload.split("\\Q.\\E", 2);
      Sampler sampler = resolveSampler(parts[0]);
      if (sampler == null) {
        return null;
      }

      String objectPath = parts.length > 1 ? parts[1] : "";
      return new SectionTarget("sampler:" + parts[0], resolveSectionObject(sampler, objectPath, createMissing));
    }

    return null;
  }

  private static EditTarget resolveEditTarget(String path) {
    String normalized = normalizePath(path);
    if (normalized.isBlank()) {
      return null;
    }

    if (normalized.equals(ROOT_MAIN) || normalized.startsWith(ROOT_MAIN + ".")) {
      String objectPath = stripPrefix(normalized, ROOT_MAIN);
      if (objectPath.isBlank()) {
        return null;
      }

      return new EditTarget(
          "main-config",
          ReactConfiguration.get(),
          objectPath,
          React.instance.getDataFile("config.toml"),
          ReactConfigGUI::reloadMainConfig,
          () -> {
          }
      );
    }

    if (normalized.equals(ROOT_CORE) || normalized.startsWith(ROOT_CORE + ".")) {
      String payload = stripPrefix(normalized, ROOT_CORE);
      if (payload.isBlank()) {
        return null;
      }

      String[] parts = payload.split("\\Q.\\E", 2);
      if (parts.length < 2 || parts[1].isBlank()) {
        return null;
      }

      IController controller = resolveController(parts[0]);
      if (controller == null) {
        return null;
      }

      return new EditTarget(
          "core:" + parts[0],
          controller,
          parts[1],
          React.instance.getDataFile("core", parts[0] + ".toml"),
          () -> reloadCoreConfig(parts[0]),
          () -> {
          }
      );
    }

    if (normalized.equals(ROOT_FEATURE) || normalized.startsWith(ROOT_FEATURE + ".")) {
      String payload = stripPrefix(normalized, ROOT_FEATURE);
      if (payload.isBlank()) {
        return null;
      }

      String[] parts = payload.split("\\Q.\\E", 2);
      if (parts.length < 2 || parts[1].isBlank()) {
        return null;
      }

      Feature feature = resolveFeature(parts[0]);
      if (feature == null) {
        return null;
      }

      return new EditTarget(
          "feature:" + parts[0],
          feature,
          parts[1],
          React.instance.getDataFile("feature", parts[0] + ".toml"),
          () -> reloadFeatureConfig(parts[0]),
          () -> {
          }
      );
    }

    if (normalized.equals(ROOT_TWEAK) || normalized.startsWith(ROOT_TWEAK + ".")) {
      String payload = stripPrefix(normalized, ROOT_TWEAK);
      if (payload.isBlank()) {
        return null;
      }

      String[] parts = payload.split("\\Q.\\E", 2);
      if (parts.length < 2 || parts[1].isBlank()) {
        return null;
      }

      Tweak tweak = resolveTweak(parts[0]);
      if (tweak == null) {
        return null;
      }

      return new EditTarget(
          "tweak:" + parts[0],
          tweak,
          parts[1],
          React.instance.getDataFile("tweak", parts[0] + ".toml"),
          () -> reloadTweakConfig(parts[0]),
          () -> {
          }
      );
    }

    if (normalized.equals(ROOT_ACTION) || normalized.startsWith(ROOT_ACTION + ".")) {
      String payload = stripPrefix(normalized, ROOT_ACTION);
      if (payload.isBlank()) {
        return null;
      }

      String[] parts = payload.split("\\Q.\\E", 2);
      if (parts.length < 2 || parts[1].isBlank()) {
        return null;
      }

      Action<?> action = resolveAction(parts[0]);
      if (action == null) {
        return null;
      }

      return new EditTarget(
          "action:" + parts[0],
          action,
          parts[1],
          React.instance.getDataFile("action", parts[0] + ".toml"),
          () -> reloadActionConfig(parts[0]),
          () -> {
          }
      );
    }

    if (normalized.equals(ROOT_SAMPLER) || normalized.startsWith(ROOT_SAMPLER + ".")) {
      String payload = stripPrefix(normalized, ROOT_SAMPLER);
      if (payload.isBlank()) {
        return null;
      }

      String[] parts = payload.split("\\Q.\\E", 2);
      if (parts.length < 2 || parts[1].isBlank()) {
        return null;
      }

      Sampler sampler = resolveSampler(parts[0]);
      if (sampler == null) {
        return null;
      }

      return new EditTarget(
          "sampler:" + parts[0],
          sampler,
          parts[1],
          React.instance.getDataFile("sampler", parts[0] + ".toml"),
          () -> reloadSamplerConfig(parts[0]),
          () -> {
          }
      );
    }

    return null;
  }

  private static boolean reloadMainConfig() {
    boolean loaded = ReactConfiguration.reload();
    if (loaded) {
      refreshGlobalRuntimeSettings();
    }
    return loaded;
  }

  private static boolean reloadCoreConfig(String id) {
    IController controller = resolveController(id);
    if (controller == null) {
      return false;
    }

    return runSync(() -> {
      controller.loadConfiguration();
      if (controller instanceof HotloadController hotloadController) {
        hotloadController.refreshAfterConfigReload();
      }
      if (controller instanceof PlayerController playerController) {
        playerController.updateMonitors();
      }
      return true;
    });
  }

  private static boolean reloadFeatureConfig(String id) {
    FeatureController controller = React.controller(FeatureController.class);
    if (controller == null || controller.getFeatures() == null) {
      return false;
    }

    Feature feature = controller.getFeatures().get(id);
    if (feature == null) {
      return false;
    }

    return runSync(() -> {
      boolean wasActive = controller.getActiveFeatures().containsKey(feature.getId());
      feature.loadConfiguration();
      boolean nowEnabled = feature.isEnabled();

      if (wasActive && !nowEnabled) {
        controller.deactivateFeature(feature);
      } else if (!wasActive && nowEnabled) {
        controller.activateFeature(feature);
      } else if (wasActive) {
        controller.deactivateFeature(feature);
        controller.activateFeature(feature);
      }
      return true;
    });
  }

  private static boolean reloadTweakConfig(String id) {
    TweakController controller = React.controller(TweakController.class);
    if (controller == null || controller.getTweaks() == null) {
      return false;
    }

    Tweak tweak = controller.getTweaks().get(id);
    if (tweak == null) {
      return false;
    }

    return runSync(() -> {
      boolean wasActive = controller.getActiveTweaks().containsKey(tweak.getId());
      tweak.loadConfiguration();
      boolean nowEnabled = tweak.isEnabled();

      if (wasActive && !nowEnabled) {
        controller.deactivateTweak(tweak);
      } else if (!wasActive && nowEnabled) {
        controller.activateTweak(tweak);
      } else if (wasActive) {
        controller.deactivateTweak(tweak);
        controller.activateTweak(tweak);
      }
      return true;
    });
  }

  private static boolean reloadActionConfig(String id) {
    ActionController controller = React.controller(ActionController.class);
    if (controller == null || controller.getActions() == null) {
      return false;
    }

    Action<?> action = controller.getActions().get(id);
    if (action == null) {
      return false;
    }

    return runSync(() -> {
      action.loadConfiguration();
      return true;
    });
  }

  private static boolean reloadSamplerConfig(String id) {
    SampleController controller = React.controller(SampleController.class);
    if (controller == null || controller.getSamplers() == null) {
      return false;
    }

    if (controller.getSamplers().get(id) == null) {
      return false;
    }

    return runSync(() -> {
      boolean ok = controller.reloadSamplerConfig(id);
      if (!ok) {
        return false;
      }

      PlayerController playerController = React.controller(PlayerController.class);
      if (playerController != null) {
        playerController.updateMonitors();
      }
      return true;
    });
  }

  private static boolean runSync(BooleanSupplier supplier) {
    return J.sResult(() -> {
      try {
        return supplier.getAsBoolean();
      } catch (Throwable e) {
        React.warn("Config GUI apply failed: " + e.getMessage());
        return false;
      }
    });
  }

  private static void refreshGlobalRuntimeSettings() {
    J.s(() -> {
      EntityController entityController = React.controller(EntityController.class);
      if (entityController != null) {
        ReactConfiguration.get().getPriority().rebuildPriority();
      }

      PlayerController playerController = React.controller(PlayerController.class);
      if (playerController != null) {
        playerController.updateMonitors();
      }
    });
  }

  private static IController resolveController(String id) {
    if (id == null || id.isBlank() || React.instance == null || React.instance.getControllerRegistry() == null) {
      return null;
    }
    return React.instance.getControllerRegistry().get(id);
  }

  private static Feature resolveFeature(String id) {
    FeatureController controller = React.controller(FeatureController.class);
    if (controller == null || controller.getFeatures() == null || id == null || id.isBlank()) {
      return null;
    }
    return controller.getFeatures().get(id);
  }

  private static Tweak resolveTweak(String id) {
    TweakController controller = React.controller(TweakController.class);
    if (controller == null || controller.getTweaks() == null || id == null || id.isBlank()) {
      return null;
    }
    return controller.getTweaks().get(id);
  }

  private static Action<?> resolveAction(String id) {
    ActionController controller = React.controller(ActionController.class);
    if (controller == null || controller.getActions() == null || id == null || id.isBlank()) {
      return null;
    }
    return controller.getActions().get(id);
  }

  private static Sampler resolveSampler(String id) {
    SampleController controller = React.controller(SampleController.class);
    if (controller == null || controller.getSamplers() == null || id == null || id.isBlank()) {
      return null;
    }
    return controller.getSamplers().get(id);
  }

  private static String normalizePath(String path) {
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

  private static String parentPath(String path) {
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
    String normalized = normalizePath(path);
    if (normalized.isBlank()) {
      return false;
    }

    String[] segments = normalized.split("\\Q.\\E");
    if (segments.length == 0) {
      return false;
    }

    StringBuilder parentPath = new StringBuilder();
    for (int i = 0; i < segments.length - 1; i++) {
      if (i > 0) {
        parentPath.append('.');
      }
      parentPath.append(segments[i]);
    }

    Object section = resolveSectionObject(root, parentPath.toString(), createMissing);
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
    String normalized = normalizePath(path);
    if (normalized.isBlank()) {
      return null;
    }

    String[] segments = normalized.split("\\Q.\\E");
    if (segments.length == 0) {
      return null;
    }

    StringBuilder parentPath = new StringBuilder();
    for (int i = 0; i < segments.length - 1; i++) {
      if (i > 0) {
        parentPath.append('.');
      }
      parentPath.append(segments[i]);
    }

    Object section = resolveSectionObject(root, parentPath.toString(), false);
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
    Class<?> valueType = value.getClass();
    if (normalizedTarget != null && normalizedTarget.isAssignableFrom(valueType)) {
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
    if (Map.class.isAssignableFrom(normalized) || Collection.class.isAssignableFrom(normalized) || normalized.isArray()) {
      return false;
    }
    return true;
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
      if (constant == null) {
        continue;
      }

      if (constant.toString().equalsIgnoreCase(value)) {
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
      if (constant == null) {
        continue;
      }
      values.add(constant.toString());
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

  private static int centeredPosition(int index, int rowCount) {
    int safeCount = Math.max(1, Math.min(9, rowCount));
    int safeIndex = Math.max(0, Math.min(index, safeCount - 1));
    int start = -(safeCount / 2);
    if ((safeCount & 1) == 1) {
      start = -((safeCount - 1) / 2);
    }
    return start + safeIndex;
  }

  private static void navigateTo(Player player, String path, int page) {
    if (player == null) {
      return;
    }
    open(player, path, page);
  }

  private static void playPageTurn(Player player) {
    if (player == null || !player.isOnline()) {
      return;
    }

    try {
      player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
      player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
      player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);
    } catch (Throwable ignored) {
      // Older versions may not expose this specific sound key.
    }
  }

  private static String shortenTitle(String title) {
    String safe = title == null ? ReactLanguage.plain(GuiMessages.CONFIG_FALLBACK_TITLE) : title;
    if (safe.length() <= 32) {
      return safe;
    }
    return safe.substring(0, 29) + "...";
  }

  private static String displayName(String key) {
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

  private static String summarizeValue(Object value) {
    if (value == null) {
      return ReactLanguage.plain(GuiMessages.NULL_VALUE);
    }

    if (value instanceof Map<?, ?> map) {
      return ReactLanguage.plain(
          GuiMessages.CONFIG_VALUE_MAP,
          MessageArgument.untrusted("count", map.size())
      );
    }
    if (value instanceof Collection<?> collection) {
      return ReactLanguage.plain(
          GuiMessages.CONFIG_VALUE_LIST,
          MessageArgument.untrusted("count", collection.size())
      );
    }
    if (value.getClass().isArray()) {
      return ReactLanguage.plain(
          GuiMessages.CONFIG_VALUE_ARRAY,
          MessageArgument.untrusted("count", Array.getLength(value))
      );
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

  private record EditTarget(
      String sourceTag,
      Object rootObject,
      String objectPath,
      File file,
      BooleanSupplier reload,
      Runnable afterReload
  ) {
  }

  private record ElementDescriptor(ElementKind kind, boolean editable) {
  }

  private record FieldEntry(Field field, String path, Object value,
                            ElementDescriptor descriptor, List<String> docs) {
  }

  private record SectionIndexEntry(
      String path,
      String displayName,
      Material material,
      String lore,
      boolean highlighted,
      String quickTogglePath,
      Boolean quickToggleValue
  ) {
    private SectionIndexEntry(String path, String displayName, Material material, String lore) {
      this(path, displayName, material, lore, false, null, null);
    }

    private SectionIndexEntry(String path, String displayName, Material material, String lore, boolean highlighted) {
      this(path, displayName, material, lore, highlighted, null, null);
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
