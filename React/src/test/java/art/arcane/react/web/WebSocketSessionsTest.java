package art.arcane.react.web;

import art.arcane.react.api.web.ws.WsChannel;
import art.arcane.react.api.web.ws.WebSocketSessions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebSocketSessionsTest {

  private static final class FakeWsChannel implements WsChannel {
    private final String channelId;
    private boolean open;
    private final boolean throwOnSend;
    private final List<String> received = new ArrayList<>();

    FakeWsChannel(String channelId, boolean open, boolean throwOnSend) {
      this.channelId = channelId;
      this.open = open;
      this.throwOnSend = throwOnSend;
    }

    @Override
    public String id() {
      return channelId;
    }

    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public void send(String text) {
      if (throwOnSend) {
        throw new RuntimeException("send failed");
      }
      received.add(text);
    }

    List<String> received() {
      return received;
    }
  }

  @Test
  void it_reports_size_after_add() {
    WebSocketSessions sessions = new WebSocketSessions();
    FakeWsChannel a = new FakeWsChannel("a", true, false);
    FakeWsChannel b = new FakeWsChannel("b", true, false);
    sessions.add(a);
    sessions.add(b);
    assertEquals(2, sessions.size());
    assertFalse(sessions.isEmpty());
  }

  @Test
  void it_broadcasts_text_to_all_open_channels_and_returns_count() {
    WebSocketSessions sessions = new WebSocketSessions();
    FakeWsChannel a = new FakeWsChannel("a", true, false);
    FakeWsChannel b = new FakeWsChannel("b", true, false);
    sessions.add(a);
    sessions.add(b);
    int count = sessions.broadcast("x");
    assertEquals(2, count);
    assertEquals(List.of("x"), a.received());
    assertEquals(List.of("x"), b.received());
  }

  @Test
  void it_skips_and_removes_closed_channel() {
    WebSocketSessions sessions = new WebSocketSessions();
    FakeWsChannel open = new FakeWsChannel("open", true, false);
    FakeWsChannel closed = new FakeWsChannel("closed", false, false);
    sessions.add(open);
    sessions.add(closed);
    int count = sessions.broadcast("hello");
    assertEquals(1, count);
    assertEquals(1, sessions.size());
    assertTrue(closed.received().isEmpty());
    assertEquals(List.of("hello"), open.received());
  }

  @Test
  void it_removes_channel_that_throws_on_send_and_continues() {
    WebSocketSessions sessions = new WebSocketSessions();
    FakeWsChannel thrower = new FakeWsChannel("thrower", true, true);
    FakeWsChannel good = new FakeWsChannel("good", true, false);
    sessions.add(thrower);
    sessions.add(good);
    int count = sessions.broadcast("msg");
    assertEquals(1, count);
    assertFalse(sessions.isEmpty());
    assertEquals(1, sessions.size());
    assertEquals(List.of("msg"), good.received());
  }

  @Test
  void it_remove_drops_channel() {
    WebSocketSessions sessions = new WebSocketSessions();
    FakeWsChannel a = new FakeWsChannel("a", true, false);
    sessions.add(a);
    sessions.remove("a");
    int count = sessions.broadcast("nothing");
    assertEquals(0, count);
  }
}
