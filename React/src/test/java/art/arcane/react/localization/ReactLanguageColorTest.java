package art.arcane.react.localization;

import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.volmlib.util.config.TomlCodec;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.PluralSelector;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactLanguageColorTest {
  private static final MiniMessage MINI_MESSAGE = MiniMessage.builder().strict(true).build();

  @AfterEach
  void restoreEnglish() {
    assertTrue(ReactLanguage.reloadCandidate(LocalizationCandidate.english(
        ReactMessages.catalog(), PluralSelector.oneOther())).applied());
  }

  @ParameterizedTest
  @CsvSource({
      "&0,black", "&1,dark_blue", "&2,dark_green", "&3,dark_aqua",
      "&4,dark_red", "&5,dark_purple", "&6,gold", "&7,gray",
      "&8,dark_gray", "&9,blue", "&a,green", "&b,aqua",
      "&c,red", "&d,light_purple", "&e,yellow", "&f,white",
      "&k,obfuscated", "&l,bold", "&m,strikethrough", "&n,underlined", "&o,italic"
  })
  void rendersClassicColorsAndDecorations(String code, String tag) throws Exception {
    install(code + "React {version}&r");

    assertEquals(MINI_MESSAGE.deserialize("<" + tag + ">React 2.0</" + tag + ">").compact(),
        renderVersion("2.0").compact());
  }

  @ParameterizedTest
  @ValueSource(strings = {"&#12ABef", "&x12ABef", "&x&1&2&A&B&e&f", "[12ABef]", "§x§1§2§a§b§e§f"})
  void rendersSupportedRgbColors(String code) throws Exception {
    install(code + "React {version}");

    assertEquals(TextColor.color(0x12abef), renderVersion("2.0").color());
    assertEquals("React 2.0", ReactLanguage.plain(CommandMessages.VERSION,
        MessageArgument.untrusted("version", "2.0")));
  }

  @Test
  void colorChangesResetDecorationsAndCodesCanRestoreThem() throws Exception {
    install("&3&lHeading &f{version}&3&l end&r");

    assertEquals(MINI_MESSAGE.deserialize(
        "<dark_aqua><bold>Heading </bold></dark_aqua><white>2.0</white>"
            + "<dark_aqua><bold> end</bold></dark_aqua>").compact(), renderVersion("2.0").compact());
  }

  @Test
  void resetRestoresUnformattedText() throws Exception {
    install("&c&lReact&r {version}");

    assertEquals(MINI_MESSAGE.deserialize("<red><bold>React</bold></red> 2.0").compact(),
        renderVersion("2.0").compact());
  }

  @Test
  void untrustedArgumentsCannotInjectLegacyOrMiniMessageFormatting() throws Exception {
    install("&cReact {version}");
    String version = "&a&#123456[abcdef]<bold>value</bold>";

    assertEquals(Component.text("React " + version, TextColor.color(0xff5555)), renderVersion(version));
  }

  @Test
  void escapedColorCodesRemainLiteral() throws Exception {
    install("\\&cReact \\[12ABef]{version}");

    assertEquals(Component.text("&cReact [12ABef]2.0"), renderVersion("2.0"));
  }

  @Test
  void advancedMiniMessageKeepsItsColorsAndInteractiveEvents() throws Exception {
    String template = "<click:run_command:'/react help'><hover:show_text:'Details'>"
        + "<red>React {version}</red></hover></click>";
    install(template);

    assertEquals(MINI_MESSAGE.deserialize(template.replace("{version}", "2.0")).compact(),
        renderVersion("2.0").compact());
  }

  @Test
  void recognizedMiniMessageDoesNotInterpretMixedLegacyCodes() throws Exception {
    install("<red>&lReact {version}</red>");

    assertEquals(MINI_MESSAGE.deserialize("<red>&lReact 2.0</red>"), renderVersion("2.0"));
  }

  @Test
  void malformedMiniMessageStillFailsStrictValidation() {
    assertThrows(IllegalArgumentException.class, () -> parse("<red>React {version}"));
    assertThrows(IllegalArgumentException.class, () -> parse("<click:run_command:'{version}'>React</click>"));
  }

  @Test
  void rendererValuesKeepColorCodesAndTagsAsPlainText() {
    String value = "&c<red>Offline</red>";
    LocaleOverlay overlay = LocaleOverlay.builder("renderer-text", "de_DE")
        .text(RendererMessages.STATUS_OFFLINE.id(), value).build();
    assertTrue(ReactLanguage.reloadCandidate(new LocalizationCandidate(
        ReactMessages.catalog(), List.of(overlay), PluralSelector.oneOther())).applied());

    assertEquals(value, ReactLanguage.raw(RendererMessages.STATUS_OFFLINE));
    assertEquals(Component.text(value), ReactLanguage.component(RendererMessages.STATUS_OFFLINE));
  }

  private static void install(String template) throws Exception {
    assertTrue(ReactLanguage.reloadCandidate(new LocalizationCandidate(
        ReactMessages.catalog(), List.of(parse(template)), PluralSelector.oneOther())).applied());
  }

  private static LocaleOverlay parse(String template) throws Exception {
    JsonObject messages = new JsonObject();
    messages.addProperty(CommandMessages.VERSION.id(), template);
    return ReactLanguage.parseOverlay("color-test", "de_DE", TomlCodec.toToml(messages));
  }

  private static Component renderVersion(String version) {
    return ReactLanguage.component(CommandMessages.VERSION, MessageArgument.untrusted("version", version));
  }
}
