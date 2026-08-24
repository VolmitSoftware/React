package art.arcane.react.web;

import art.arcane.react.api.web.ws.WebSocketSessions;
import art.arcane.react.api.web.ws.WsAuthenticationGate;
import art.arcane.react.api.web.ws.WsChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

class WsAuthenticationGateTest {

  @Test
  void exactFirstFrameAuthenticatesRequiredScope() {
    AtomicReference<String> closeReason = new AtomicReference<>();
    WebSocketSessions sessions = new WebSocketSessions();
    WsAuthenticationGate gate = new WsAuthenticationGate(
        4,
        5000L,
        (token, scope) -> token.equals("bearer") && scope.equals("read")
    );

    Assertions.assertTrue(gate.register(
        new OpenChannel("one"),
        closeReason::set,
        "read",
        sessions
    ));
    Assertions.assertTrue(gate.authenticate(
        "one",
        "{\"type\":\"auth\",\"token\":\"bearer\"}",
        sessions,
        closeReason::set
    ));

    Assertions.assertEquals(1, sessions.size());
    Assertions.assertEquals(0, gate.pendingCount());
    Assertions.assertNull(closeReason.get());
  }

  @Test
  void malformedInvalidAndRepeatedAuthenticationAreRejected() {
    WebSocketSessions sessions = new WebSocketSessions();
    WsAuthenticationGate gate = new WsAuthenticationGate(
        4,
        5000L,
        (token, scope) -> token.equals("bearer")
    );
    AtomicReference<String> malformedReason = new AtomicReference<>();
    gate.register(new OpenChannel("malformed"), malformedReason::set, "read", sessions);
    Assertions.assertFalse(gate.authenticate(
        "malformed",
        "{\"type\":\"auth\",\"token\":\"bearer\",\"extra\":true}",
        sessions,
        malformedReason::set
    ));
    Assertions.assertNotNull(malformedReason.get());

    AtomicReference<String> invalidReason = new AtomicReference<>();
    gate.register(new OpenChannel("invalid"), invalidReason::set, "read", sessions);
    Assertions.assertFalse(gate.authenticate(
        "invalid",
        "{\"type\":\"auth\",\"token\":\"wrong\"}",
        sessions,
        invalidReason::set
    ));
    Assertions.assertNotNull(invalidReason.get());

    AtomicReference<String> repeatedReason = new AtomicReference<>();
    gate.register(new OpenChannel("repeated"), repeatedReason::set, "read", sessions);
    Assertions.assertTrue(gate.authenticate(
        "repeated",
        "{\"type\":\"auth\",\"token\":\"bearer\"}",
        sessions,
        repeatedReason::set
    ));
    Assertions.assertFalse(gate.authenticate(
        "repeated",
        "{\"type\":\"auth\",\"token\":\"bearer\"}",
        sessions,
        repeatedReason::set
    ));
    Assertions.assertEquals(0, sessions.size());
    Assertions.assertNotNull(repeatedReason.get());
  }

  @Test
  void unauthenticatedCapacityAndLifetimeAreBounded() {
    int maximumPending = 2048;
    AtomicLong clock = new AtomicLong(0L);
    AtomicInteger closes = new AtomicInteger();
    WebSocketSessions sessions = new WebSocketSessions();
    WsAuthenticationGate gate = new WsAuthenticationGate(
        maximumPending,
        5000L,
        (token, scope) -> true,
        clock::get
    );

    for (int index = 0; index < maximumPending; index++) {
      Assertions.assertTrue(gate.register(
          new OpenChannel("pending-" + index),
          reason -> closes.incrementAndGet(),
          "read",
          sessions
      ));
    }
    Assertions.assertFalse(gate.register(
        new OpenChannel("overflow"),
        reason -> closes.incrementAndGet(),
        "read",
        sessions
    ));
    Assertions.assertEquals(maximumPending, gate.pendingCount());
    Assertions.assertEquals(1, closes.get());

    clock.set(5_000_000_000L);
    Assertions.assertEquals(maximumPending, gate.expire());
    Assertions.assertEquals(0, gate.pendingCount());
    Assertions.assertEquals(maximumPending + 1, closes.get());
  }

  @Test
  void optionalReadAuthenticationAcceptsOneValidClientFrame() {
    AtomicReference<String> closeReason = new AtomicReference<>();
    WebSocketSessions sessions = new WebSocketSessions();
    sessions.add(new OpenChannel("optional"));
    WsAuthenticationGate gate = new WsAuthenticationGate(
        4,
        5000L,
        (token, scope) -> token.equals("bearer") && scope.equals("read")
    );

    Assertions.assertTrue(gate.authenticateOptional(
        "optional",
        "{\"type\":\"auth\",\"token\":\"bearer\"}",
        "read",
        sessions,
        closeReason::set
    ));
    Assertions.assertEquals(1, sessions.size());
    Assertions.assertNull(closeReason.get());

    Assertions.assertFalse(gate.authenticateOptional(
        "optional",
        "{\"type\":\"auth\",\"token\":\"bearer\"}",
        "read",
        sessions,
        closeReason::set
    ));
    Assertions.assertEquals(0, sessions.size());
    Assertions.assertNotNull(closeReason.get());
  }

  private static final class OpenChannel implements WsChannel {
    private final String channelId;

    private OpenChannel(String channelId) {
      this.channelId = channelId;
    }

    @Override
    public String id() {
      return channelId;
    }

    @Override
    public boolean isOpen() {
      return true;
    }

    @Override
    public void send(String text) {
    }
  }
}
