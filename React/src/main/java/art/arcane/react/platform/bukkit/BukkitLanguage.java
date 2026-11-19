package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;

/**
 * The Bukkit language menu and {@code /react language} switcher on top of {@link ReactLanguage}.
 */
public final class BukkitLanguage {
  private static BukkitLanguageSwitcher languageSwitcher;

  private BukkitLanguage() {
  }

  public static boolean initialize() {
    closeSwitcher();
    boolean loaded = ReactLanguage.initialize();
    languageSwitcher = BukkitLanguageSwitcher.register(React.instance, ReactLanguage.selections(),
        new BukkitLanguageSwitcher.Options("react", "react.use",
            DirectorMiniMenu.Theme.reactBlue(), ReactLanguage.directorResolver(), ReactLanguage.editorOptions(), null,
            ReactLanguage.updateAction(), ReactLanguage.displayNames()));
    return loaded;
  }

  public static void close() {
    closeSwitcher();
    ReactLanguage.close();
  }

  public static BukkitLanguageSwitcher switcher() {
    return languageSwitcher;
  }

  private static void closeSwitcher() {
    if (languageSwitcher != null) {
      languageSwitcher.close();
      languageSwitcher = null;
    }
  }
}
