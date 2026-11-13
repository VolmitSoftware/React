package art.arcane.react.content.directorcommand;

import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebRole;
import art.arcane.react.api.web.pairing.WebTokens;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CommandWebTest {
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
  private static final String URL = "https://react.volmitsoftware.com/pair#RCT3.secret-payload";

  @Test
  void thePlayerGetsOneLineWithOpenCopyAndRevokeButNoVisibleLink() {
    Component reply = CommandWeb.playerReply(URL, "tok_AAAAAAAAAAAAAAAA");
    String visible = PLAIN.serialize(reply);

    Assertions.assertTrue(visible.contains("Link ready. Open it within 10 minutes"), visible);
    Assertions.assertTrue(visible.contains("[Open React Web]") && visible.contains("[Copy]") && visible.contains("[Revoke]"), visible);
    Assertions.assertFalse(visible.contains(URL));
    Assertions.assertFalse(visible.contains("\n"));

    List<ClickEvent> clicks = new ArrayList<>();
    reply.children().forEach(child -> {
      if (child.clickEvent() != null) {
        clicks.add(child.clickEvent());
      }
    });
    Assertions.assertEquals(List.of(ClickEvent.Action.OPEN_URL, ClickEvent.Action.COPY_TO_CLIPBOARD, ClickEvent.Action.SUGGEST_COMMAND),
        clicks.stream().map(ClickEvent::action).toList());
    Assertions.assertEquals(URL, ((ClickEvent.Payload.Text) clicks.get(0).payload()).value());
    Assertions.assertEquals(URL, ((ClickEvent.Payload.Text) clicks.get(1).payload()).value());
    Assertions.assertEquals("/react web revoke tok_AAAAAAAAAAAAAAAA", ((ClickEvent.Payload.Text) clicks.get(2).payload()).value());
  }

  @Test
  void theConsoleGetsTheLinkThenScopesLifetimeAndId() {
    WebTokens.Grant grant = new WebTokens.Grant("", WebRole.OPERATOR.scopes(), Duration.ofDays(7), "CONSOLE", Optional.empty());
    List<Component> lines = CommandWeb.consoleReply(URL, grant, "tok_AAAAAAAAAAAAAAAA");

    Assertions.assertEquals(2, lines.size());
    Assertions.assertTrue(PLAIN.serialize(lines.get(0)).contains(URL));
    Assertions.assertEquals("Scopes read op:execute (operator); ends after 7d unused; token tok_AAAAAAAAAAAAAAAA",
        PLAIN.serialize(lines.get(1)).substring(PLAIN.serialize(lines.get(1)).indexOf("Scopes")));
    Assertions.assertNull(lines.get(0).clickEvent());
  }

  @Test
  void optionsDefaultToTheOperatorSetForTokenDays() throws Exception {
    WebTokens.Grant grant = CommandWeb.grant("", "", "", "", 7, "CONSOLE", Optional.empty());
    Assertions.assertEquals(List.of("read", "op:execute"), grant.scopes());
    Assertions.assertEquals(Duration.ofDays(7), grant.lifetime());
    Assertions.assertEquals("", grant.label());
  }

  @Test
  void aRoleOrScopesAndALifetimeNarrowOrWidenIt() throws Exception {
    UUID player = UUID.randomUUID();
    WebTokens.Grant admin = CommandWeb.grant("laptop", "admin", "", "12h", 7, "Brian", Optional.of(player));
    Assertions.assertEquals(WebRole.ADMIN.scopes(), admin.scopes());
    Assertions.assertEquals(Duration.ofHours(12), admin.lifetime());
    Assertions.assertEquals(Optional.of(player), admin.issuerId());

    WebTokens.Grant custom = CommandWeb.grant("", "", "console:read,read", "30m", 7, "CONSOLE", Optional.empty());
    Assertions.assertEquals(List.of("read", "console:read"), custom.scopes());
    Assertions.assertEquals(Duration.ofMinutes(30), custom.lifetime());
  }

  @Test
  void badOptionsAreRefusedWithTheirOwnMessage() {
    Assertions.assertEquals(CommandMessages.WEB_ROLE_AND_SCOPES, refusal("", "admin", "read", ""));
    Assertions.assertEquals(CommandMessages.WEB_INVALID_ROLE, refusal("", "owner", "", ""));
    Assertions.assertEquals(CommandMessages.WEB_INVALID_SCOPE, refusal("", "", "read,shell", ""));
    Assertions.assertEquals(CommandMessages.WEB_SCOPES_NEED_READ, refusal("", "", "op:execute", ""));
    Assertions.assertEquals(CommandMessages.WEB_INVALID_EXPIRES, refusal("", "", "", "7w"));
    Assertions.assertEquals(CommandMessages.WEB_INVALID_EXPIRES, refusal("", "", "", "366d"));
    Assertions.assertEquals(CommandMessages.WEB_INVALID_LABEL, refusal("x".repeat(65), "", "", ""));
  }

  @Test
  void roleParameterOffersEverySupportedRole() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new CommandReact());
    DirectorSender sender = Mockito.mock(DirectorSender.class);

    List<String> suggestions = engine.tabComplete(new DirectorInvocation(
        sender,
        "react",
        List.of("web", "pair", "label=device", "role=")
    ));

    Assertions.assertEquals(List.of("role=admin", "role=operator", "role=viewer"), suggestions);
  }

  @Test
  void theWebCommandsAreAnimusNamesWithNoOldOnes() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new CommandReact());
    DirectorSender sender = Mockito.mock(DirectorSender.class);

    List<String> suggestions = engine.tabComplete(new DirectorInvocation(sender, "react", List.of("web", "")));

    Assertions.assertTrue(suggestions.containsAll(List.of("pair", "status", "tokens", "revoke")), suggestions.toString());
    Assertions.assertFalse(suggestions.contains("list"), suggestions.toString());
  }

  @Test
  void anUnreachableListenerMakesNoLink(@TempDir File dataFolder) {
    WebController disabled = controller(dataFolder);
    disabled.getConfig().setListenerEnabled(false);
    assertNoLink(disabled, dataFolder);

    WebController unbound = controller(dataFolder);
    assertNoLink(unbound, dataFolder);

    WebController failed = controller(dataFolder);
    failed.setStartFailure(new IllegalStateException("bind failed"));
    assertNoLink(failed, dataFolder);
  }

  private static CommandWeb.OptionRefused refusalOf(String label, String role, String scopes, String expires) {
    return Assertions.assertThrows(CommandWeb.OptionRefused.class,
        () -> CommandWeb.grant(label, role, scopes, expires, 7, "CONSOLE", Optional.empty()));
  }

  private static Object refusal(String label, String role, String scopes, String expires) {
    return refusalOf(label, role, scopes, expires).key;
  }

  private static WebController controller(File dataFolder) {
    WebController controller = new WebController();
    controller.setConfig(new WebConfiguration());
    controller.setDataFolder(dataFolder);
    return controller;
  }

  private static void assertNoLink(WebController controller, File dataFolder) {
    Assertions.assertThrows(
        IllegalStateException.class,
        () -> controller.createLink(new WebTokens.Grant("", WebRole.OPERATOR.scopes(), Duration.ofDays(7), "CONSOLE", Optional.empty()))
    );
    Assertions.assertNull(controller.getTokens());
    Assertions.assertFalse(new File(dataFolder, WebTokens.FILE).exists());
  }
}
