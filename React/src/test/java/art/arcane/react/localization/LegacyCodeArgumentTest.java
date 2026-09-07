package art.arcane.react.localization;

import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.localization.catalog.EnvironmentMessages;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class LegacyCodeArgumentTest {
  @Test
  public void hardwareValuesCarryingLegacyColourCodesStillRender() {
    String rendered = ReactLanguage.plain(
        EnvironmentMessages.GRAPHICS_ENTRY,
        MessageArgument.untrusted("value", "§9Gpu Model: §7AMD Radeon(TM) Graphics")
    );

    Assertions.assertEquals(" Gpu Model: AMD Radeon(TM) Graphics", rendered);
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
      "§l§n1.2.3§r, React 1.2.3",
      "1.2.3§, React 1.2.3",
      "1.2.3§ build, React 1.2.3 build",
      "§c<red>unsafe</red>, React <red>unsafe</red>"
  })
  public void legacyCodesAreStrippedFromEveryUntrustedArgument(String version, String expected) {
    String rendered = ReactLanguage.plain(
        CommandMessages.VERSION,
        MessageArgument.untrusted("version", version)
    );

    Assertions.assertEquals(expected, rendered);
  }

  @Test
  public void commandFailureTextCarryingLegacyCodesStillRenders() {
    String rendered = ReactLanguage.plain(
        DirectorRuntimeMessages.EXECUTION_FAILED,
        MessageArgument.untrusted("command", "react environment info"),
        MessageArgument.untrusted("reason", "Legacy codes detected in §9Gpu Model: §7AMD")
    );

    Assertions.assertFalse(rendered.contains("§"), rendered);
    Assertions.assertTrue(rendered.contains("Gpu Model: AMD"), rendered);
  }
}
