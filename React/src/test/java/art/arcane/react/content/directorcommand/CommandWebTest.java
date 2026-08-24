package art.arcane.react.content.directorcommand;

import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebRole;
import art.arcane.react.core.controller.WebController;
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
import java.util.List;

public class CommandWebTest {
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

  @Test
  public void playerPairingMessageCopiesCodeWithoutDisplayingIt() {
    String code = "RCT2.secret-pairing-payload";

    Component component = CommandWeb.playerPairingCodeComponent(code);
    ClickEvent clickEvent = component.clickEvent();
    String visibleText = PLAIN.serialize(component);

    Assertions.assertNotNull(clickEvent);
    Assertions.assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, clickEvent.action());
    ClickEvent.Payload.Text payload = Assertions.assertInstanceOf(
        ClickEvent.Payload.Text.class,
        clickEvent.payload()
    );
    Assertions.assertEquals(code, payload.value());
    Assertions.assertTrue(visibleText.contains("Click to copy"));
    Assertions.assertFalse(visibleText.contains(code));
  }

  @Test
  public void consolePairingMessageDisplaysRawCodeWithoutClickAction() {
    String code = "RCT2.console-pairing-payload";

    Component component = CommandWeb.consolePairingCodeComponent(code);
    String visibleText = PLAIN.serialize(component);

    Assertions.assertNull(component.clickEvent());
    Assertions.assertTrue(visibleText.contains(code));
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
  void disabledUnboundAndFailedListenersPersistNoToken(@TempDir File dataFolder) {
    WebController disabled = controller(dataFolder);
    disabled.getConfig().setListenerEnabled(false);
    assertPairingRefusedWithoutToken(disabled, dataFolder);

    WebController unbound = controller(dataFolder);
    assertPairingRefusedWithoutToken(unbound, dataFolder);

    WebController failed = controller(dataFolder);
    failed.setStartFailure(new IllegalStateException("bind failed"));
    assertPairingRefusedWithoutToken(failed, dataFolder);
  }

  @Test
  void startingListenerPersistsNoToken(@TempDir File dataFolder) {
    WebController starting = new WebController() {
      @Override
      protected void executeAsync(Runnable runnable) {
      }
    };
    starting.setDataFolder(dataFolder);
    starting.postStart();

    Assertions.assertTrue(starting.pairingUnavailableReason().contains("starting"));
    Assertions.assertThrows(
        IllegalStateException.class,
        () -> CommandWeb.createPairing(starting, "device", WebRole.VIEWER)
    );
    Assertions.assertTrue(starting.getTokenStore().all().isEmpty());
    Assertions.assertFalse(starting.tokensFile().exists());
    starting.stop();
  }

  private static WebController controller(File dataFolder) {
    WebController controller = new WebController();
    controller.setConfig(new WebConfiguration());
    controller.setDataFolder(dataFolder);
    return controller;
  }

  private static void assertPairingRefusedWithoutToken(WebController controller, File dataFolder) {
    Assertions.assertThrows(
        IllegalStateException.class,
        () -> CommandWeb.createPairing(controller, "device", WebRole.VIEWER)
    );
    Assertions.assertNull(controller.getTokenStore());
    Assertions.assertFalse(new File(dataFolder, "web/tokens.toml").exists());
  }
}
