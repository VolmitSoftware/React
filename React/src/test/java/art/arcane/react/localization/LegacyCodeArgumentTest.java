package art.arcane.react.localization;

import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.localization.catalog.EnvironmentMessages;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LegacyCodeArgumentTest {
  @Test
  public void hardwareValuesCarryingLegacyColourCodesStillRender() {
    String rendered = ReactLanguage.plain(
        EnvironmentMessages.GRAPHICS_ENTRY,
        MessageArgument.untrusted("value", "§9Gpu Model: §7AMD Radeon(TM) Graphics")
    );

    Assertions.assertEquals(" Gpu Model: AMD Radeon(TM) Graphics", rendered);
  }

  @Test
  public void legacyCodesAreStrippedFromEveryUntrustedArgument() {
    String rendered = ReactLanguage.plain(
        CommandMessages.VERSION,
        MessageArgument.untrusted("version", "§l§n1.2.3§r")
    );

    Assertions.assertEquals("React 1.2.3", rendered);
  }

  @Test
  public void aTrailingSectionSignIsDroppedRatherThanCrashing() {
    String rendered = ReactLanguage.plain(
        CommandMessages.VERSION,
        MessageArgument.untrusted("version", "1.2.3§")
    );

    Assertions.assertEquals("React 1.2.3", rendered);
  }

  @Test
  public void aSectionSignBeforeAnUnknownCharacterIsDropped() {
    String rendered = ReactLanguage.plain(
        CommandMessages.VERSION,
        MessageArgument.untrusted("version", "1.2.3§ build")
    );

    Assertions.assertEquals("React 1.2.3 build", rendered);
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

  @Test
  public void untrustedArgumentsStillCannotInjectMiniMessageTags() {
    String rendered = ReactLanguage.plain(
        CommandMessages.VERSION,
        MessageArgument.untrusted("version", "§c<red>unsafe</red>")
    );

    Assertions.assertEquals("React <red>unsafe</red>", rendered);
  }
}
