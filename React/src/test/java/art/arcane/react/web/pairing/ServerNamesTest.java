package art.arcane.react.web.pairing;

import art.arcane.react.api.web.pairing.ServerNames;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

class ServerNamesTest {
  private static final File FOLDER = new File("/srv/minecraft/lobby-1");

  @Test
  void theSettingWinsOverTheMotd() {
    Assertions.assertEquals("Hub", ServerNames.resolve(" &aHub ", "§bSkyblock", FOLDER));
  }

  @Test
  void theMotdFirstLineIsPlainText() {
    Assertions.assertEquals("Skyblock Season 4", ServerNames.resolve("", "§x§f§f§0§0§0§0<bold>Skyblock</bold>  §lSeason 4\nSecond line", FOLDER));
  }

  @Test
  void theVanillaMotdOrNothingFallsBackToTheFolder() {
    Assertions.assertEquals("lobby-1", ServerNames.resolve("", "A Minecraft Server", FOLDER));
    Assertions.assertEquals("lobby-1", ServerNames.resolve("", "§r", FOLDER));
    Assertions.assertEquals("lobby-1", ServerNames.resolve("", null, FOLDER));
  }

  @Test
  void namesAreBounded() {
    Assertions.assertEquals(64, ServerNames.resolve("x".repeat(100), "", FOLDER).length());
  }
}
