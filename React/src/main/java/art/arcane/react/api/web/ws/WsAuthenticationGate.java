package art.arcane.react.api.web.ws;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class WsAuthenticationGate {
  private static final int MAX_AUTH_FRAME_CHARS = 4096;
  private static final int MAX_TOKEN_CHARS = 2048;

  private final ConcurrentHashMap<String, PendingSession> pending = new ConcurrentHashMap<>();
  private final ConcurrentHashMap.KeySetView<String, Boolean> optionalAuthenticated = ConcurrentHashMap.newKeySet();
  private final AtomicInteger pendingCount = new AtomicInteger();
  private final int maximumPending;
  private final long timeoutNanos;
  private final BiPredicate<String, String> scopeVerifier;
  private final LongSupplier nanoTime;

  public WsAuthenticationGate(
      int maximumPending,
      long timeoutMillis,
      BiPredicate<String, String> scopeVerifier
  ) {
    this(maximumPending, timeoutMillis, scopeVerifier, System::nanoTime);
  }

  public WsAuthenticationGate(
      int maximumPending,
      long timeoutMillis,
      BiPredicate<String, String> scopeVerifier,
      LongSupplier nanoTime
  ) {
    if (maximumPending < 1) {
      throw new IllegalArgumentException("maximumPending must be at least 1");
    }
    if (timeoutMillis < 1L) {
      throw new IllegalArgumentException("timeoutMillis must be at least 1");
    }
    this.maximumPending = maximumPending;
    this.timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    this.scopeVerifier = Objects.requireNonNull(scopeVerifier, "scopeVerifier");
    this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
  }

  public boolean register(
      WsChannel channel,
      Consumer<String> policyCloser,
      String requiredScope,
      WebSocketSessions targetSessions
  ) {
    Objects.requireNonNull(channel, "channel");
    Objects.requireNonNull(policyCloser, "policyCloser");
    Objects.requireNonNull(requiredScope, "requiredScope");
    Objects.requireNonNull(targetSessions, "targetSessions");

    if (!channel.isOpen()) {
      policyCloser.accept("Connection closed before authentication");
      return false;
    }
    if (pendingCount.incrementAndGet() > maximumPending) {
      pendingCount.decrementAndGet();
      policyCloser.accept("Too many unauthenticated connections");
      return false;
    }

    PendingSession session = new PendingSession(
        channel,
        policyCloser,
        requiredScope,
        targetSessions,
        nanoTime.getAsLong() + timeoutNanos
    );
    PendingSession previous = pending.putIfAbsent(channel.id(), session);
    if (previous != null) {
      pendingCount.decrementAndGet();
      policyCloser.accept("Duplicate unauthenticated connection");
      return false;
    }
    return true;
  }

  public boolean authenticate(
      String sessionId,
      String message,
      WebSocketSessions targetSessions,
      Consumer<String> policyCloser
  ) {
    Objects.requireNonNull(sessionId, "sessionId");
    Objects.requireNonNull(targetSessions, "targetSessions");
    Objects.requireNonNull(policyCloser, "policyCloser");

    PendingSession session = removePending(sessionId);
    if (session == null || session.targetSessions() != targetSessions) {
      targetSessions.remove(sessionId);
      policyCloser.accept("Repeated or unexpected authentication frame");
      return false;
    }

    AuthFrame frame = parse(message);
    if (frame == null || !scopeVerifier.test(frame.token(), session.requiredScope())) {
      session.policyCloser().accept("Invalid authentication frame");
      return false;
    }
    if (!session.channel().isOpen()) {
      session.policyCloser().accept("Connection closed during authentication");
      return false;
    }
    targetSessions.add(session.channel());
    return true;
  }

  public boolean authenticateOptional(
      String sessionId,
      String message,
      String requiredScope,
      WebSocketSessions targetSessions,
      Consumer<String> policyCloser
  ) {
    Objects.requireNonNull(sessionId, "sessionId");
    Objects.requireNonNull(requiredScope, "requiredScope");
    Objects.requireNonNull(targetSessions, "targetSessions");
    Objects.requireNonNull(policyCloser, "policyCloser");

    AuthFrame frame = parse(message);
    if (!optionalAuthenticated.add(sessionId)
        || frame == null
        || !scopeVerifier.test(frame.token(), requiredScope)) {
      targetSessions.remove(sessionId);
      policyCloser.accept("Invalid or repeated authentication frame");
      return false;
    }
    return true;
  }

  public void reject(
      String sessionId,
      WebSocketSessions targetSessions,
      Consumer<String> policyCloser,
      String reason
  ) {
    PendingSession session = removePending(sessionId);
    targetSessions.remove(sessionId);
    if (session == null) {
      policyCloser.accept(reason);
      return;
    }
    session.policyCloser().accept(reason);
  }

  public void remove(String sessionId) {
    removePending(sessionId);
    optionalAuthenticated.remove(sessionId);
  }

  public int expire() {
    long now = nanoTime.getAsLong();
    int expired = 0;
    for (PendingSession session : pending.values()) {
      if (now < session.deadlineNanos() || !pending.remove(session.channel().id(), session)) {
        continue;
      }
      pendingCount.decrementAndGet();
      session.policyCloser().accept("Authentication timed out");
      expired++;
    }
    return expired;
  }

  public void close() {
    for (PendingSession session : pending.values()) {
      if (!pending.remove(session.channel().id(), session)) {
        continue;
      }
      pendingCount.decrementAndGet();
      session.policyCloser().accept("Server stopping");
    }
    optionalAuthenticated.clear();
  }

  public int pendingCount() {
    return pendingCount.get();
  }

  private PendingSession removePending(String sessionId) {
    PendingSession session = pending.remove(sessionId);
    if (session != null) {
      pendingCount.decrementAndGet();
    }
    return session;
  }

  private AuthFrame parse(String message) {
    if (message == null || message.length() > MAX_AUTH_FRAME_CHARS) {
      return null;
    }
    try (JsonReader reader = new JsonReader(new StringReader(message))) {
      reader.beginObject();
      String type = null;
      String token = null;
      int fields = 0;
      while (reader.hasNext()) {
        String name = reader.nextName();
        if (reader.peek() != JsonToken.STRING) {
          return null;
        }
        if (name.equals("type") && type == null) {
          type = reader.nextString();
        } else if (name.equals("token") && token == null) {
          token = reader.nextString();
        } else {
          return null;
        }
        fields++;
      }
      reader.endObject();
      if (fields != 2 || reader.peek() != JsonToken.END_DOCUMENT) {
        return null;
      }
      if (!"auth".equals(type) || token.isBlank() || token.length() > MAX_TOKEN_CHARS) {
        return null;
      }
      return new AuthFrame(token);
    } catch (IOException | RuntimeException failure) {
      return null;
    }
  }

  private record AuthFrame(String token) {
  }

  private record PendingSession(
      WsChannel channel,
      Consumer<String> policyCloser,
      String requiredScope,
      WebSocketSessions targetSessions,
      long deadlineNanos
  ) {
  }
}
