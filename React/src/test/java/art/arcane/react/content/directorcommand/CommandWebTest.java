package art.arcane.react.content.directorcommand;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
}
