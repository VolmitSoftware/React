package art.arcane.react.web;

import art.arcane.react.api.web.ws.WebSocketSessions;
import art.arcane.react.api.web.ws.WsChannel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebSocketSessionsTest {

  private static final class FakeWsChannel implements WsChannel {
    private final String channelId;
    private final boolean open;
    private final boolean throwOnSend;
    private final List<String> received = new ArrayList<>();
    private int closeCode = -1;

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
    public void ping() {
    }

    @Override
    public void close(int code, String reason) {
      closeCode = code;
    }

    void send(String text) {
      if (throwOnSend) {
        throw new RuntimeException("send failed");
      }
      received.add(text);
    }
  }

  @Test
  void it_reports_size_after_add() {
    WebSocketSessions<FakeWsChannel> sessions = new WebSocketSessions<>();
    sessions.add(new FakeWsChannel("a", true, false));
    sessions.add(new FakeWsChannel("b", true, false));
    assertEquals(2, sessions.size());
    assertFalse(sessions.isEmpty());
  }

  @Test
  void it_visits_all_open_channels_and_returns_count() {
    WebSocketSessions<FakeWsChannel> sessions = new WebSocketSessions<>();
    FakeWsChannel a = new FakeWsChannel("a", true, false);
    FakeWsChannel b = new FakeWsChannel("b", true, false);
    sessions.add(a);
    sessions.add(b);
    assertEquals(2, sessions.forEachOpen(channel -> channel.send("x")));
    assertEquals(List.of("x"), a.received);
    assertEquals(List.of("x"), b.received);
  }

  @Test
  void it_skips_and_removes_closed_channel() {
    WebSocketSessions<FakeWsChannel> sessions = new WebSocketSessions<>();
    FakeWsChannel open = new FakeWsChannel("open", true, false);
    FakeWsChannel closed = new FakeWsChannel("closed", false, false);
    sessions.add(open);
    sessions.add(closed);
    assertEquals(1, sessions.forEachOpen(channel -> channel.send("hello")));
    assertEquals(1, sessions.size());
    assertTrue(closed.received.isEmpty());
    assertEquals(List.of("hello"), open.received);
  }

  @Test
  void it_removes_channel_that_throws_and_continues() {
    WebSocketSessions<FakeWsChannel> sessions = new WebSocketSessions<>();
    FakeWsChannel good = new FakeWsChannel("good", true, false);
    sessions.add(new FakeWsChannel("thrower", true, true));
    sessions.add(good);
    assertEquals(1, sessions.forEachOpen(channel -> channel.send("msg")));
    assertEquals(1, sessions.size());
    assertEquals(List.of("msg"), good.received);
  }

  @Test
  void it_remove_drops_channel() {
    WebSocketSessions<FakeWsChannel> sessions = new WebSocketSessions<>();
    sessions.add(new FakeWsChannel("a", true, false));
    sessions.remove("a");
    assertEquals(0, sessions.forEachOpen(channel -> channel.send("nothing")));
  }

  @Test
  void closeToken_closes_only_that_tokens_channels() {
    WebSocketSessions<FakeWsChannel> sessions = new WebSocketSessions<>();
    FakeWsChannel revokedA = new FakeWsChannel("a", true, false);
    FakeWsChannel revokedB = new FakeWsChannel("b", true, false);
    FakeWsChannel other = new FakeWsChannel("c", true, false);
    FakeWsChannel bound = new FakeWsChannel("d", true, false);
    sessions.add(revokedA, "rct-1");
    sessions.add(revokedB, "rct-1");
    sessions.add(other, "rct-2");
    sessions.add(bound);
    sessions.bindToken("d", "rct-1");

    assertEquals(3, sessions.closeToken("rct-1", 4401, "Token revoked"));

    assertEquals(4401, revokedA.closeCode);
    assertEquals(4401, revokedB.closeCode);
    assertEquals(4401, bound.closeCode);
    assertEquals(-1, other.closeCode);
    assertEquals(1, sessions.size());
    assertEquals(0, sessions.closeToken(null, 4401, "none"));
  }
}
