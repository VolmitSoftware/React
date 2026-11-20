package art.arcane.react.localization;

import art.arcane.react.platform.PlatformKind;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OwnerWordingTest {
  @AfterEach
  void unbind() {
    ReactPlatforms.unbind();
  }

  @Test
  void languageKeepsPluginWordingOffMods() {
    ReactPlatforms.bind(platform(PlatformKind.BUKKIT));
    Assertions.assertEquals("Manage community Plugin API metric packs", ReactLanguage.owned("Manage community Plugin API metric packs"));
  }

  @Test
  void languageUsesModWordingOnMods() {
    ReactPlatforms.bind(platform(PlatformKind.MODDED));
    Assertions.assertEquals("Manage community Mod API metric packs", ReactLanguage.owned("Manage community Plugin API metric packs"));
  }

  private static ReactPlatform platform(PlatformKind kind) {
    ReactPlatform platform = Mockito.mock(ReactPlatform.class, Mockito.CALLS_REAL_METHODS);
    Mockito.doReturn(kind).when(platform).kind();
    return platform;
  }
  @Test
  void rewritesBukkitAsTheServer() {
    Assertions.assertEquals("Tasks waiting in the server scheduler across all mods.",
        OwnerWording.forMods("Tasks waiting in the Bukkit scheduler across all plugins."));
    Assertions.assertEquals("One tick lets the server finish adding the item entity.",
        OwnerWording.forMods("One tick lets Bukkit finish adding the item entity."));
    Assertions.assertEquals("bukkit-pending-tasks", OwnerWording.forMods("bukkit-pending-tasks"));
  }

  @Test
  void rewritesWordsInEveryCase() {
    Assertions.assertEquals("Manage community Mod API metric packs", OwnerWording.forMods("Manage community Plugin API metric packs"));
    Assertions.assertEquals("&bMod API Packs ({count})&r", OwnerWording.forMods("&bPlugin API Packs ({count})&r"));
    Assertions.assertEquals("Mod Costs", OwnerWording.forMods("Plugin Costs"));
    Assertions.assertEquals("MOD", OwnerWording.forMods("PLUGIN"));
    Assertions.assertEquals("Cross-mod integration status", OwnerWording.forMods("Cross-plugin integration status"));
    Assertions.assertEquals("Language {locale} is not available for every selected mod.",
        OwnerWording.forMods("Language {locale} is not available for every selected plugin."));
    Assertions.assertEquals("all Volmit mods", OwnerWording.forMods("all Volmit plugins"));
  }

  @Test
  void keepsIdentifiersAndPlaceholders() {
    Assertions.assertEquals("/react plugin-api status", OwnerWording.forMods("/react plugin-api status"));
    Assertions.assertEquals("{plugin}: your language is now {locale}.", OwnerWording.forMods("{plugin}: your language is now {locale}."));
    Assertions.assertEquals("<plugin> plugin.yml plugins/React plugin_api",
        OwnerWording.forMods("<plugin> plugin.yml plugins/React plugin_api"));
    Assertions.assertEquals("react.plugin-api/v1 targetPlugin pluginId", OwnerWording.forMods("react.plugin-api/v1 targetPlugin pluginId"));
  }

  @Test
  void leavesTextWithoutPluginUntouched() {
    String text = "Show loaded packs";
    Assertions.assertSame(text, OwnerWording.forMods(text));
    Assertions.assertNull(OwnerWording.forMods(null));
    Assertions.assertEquals("", OwnerWording.forMods(""));
  }
}
