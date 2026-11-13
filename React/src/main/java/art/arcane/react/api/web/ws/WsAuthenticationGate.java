package art.arcane.react.api.web.ws;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class WsAuthenticationGate {
  /** Socket close code for a pairing that was revoked or expired; the reason is {@code token_revoked} or {@code token_expired}. */
  public static final int CLOSE_PAIRING_ENDED = 4401;
  /** Socket close code for a direct upgrade from an origin outside {@code allowedOrigins}. */
  public static final int CLOSE_ORIGIN_NOT_ALLOWED = 4403;
  public static final int CLOSE_POLICY = 1008;

  /** Checks a socket's token for a scope: null admits it, anything else is the refusal reason sent with the close. */
  @FunctionalInterface
  public interface Verifier {
    String refusal(String token, String scope);
  }

  /** The close code for a refusal reason: 4401 when the pairing ended, 4403 for the origin, else 1008. */
  public static int closeCode(String reason) {
    if ("token_revoked".equals(reason) || "token_expired".equals(reason)) {
      return CLOSE_PAIRING_ENDED;
    }
    return "origin_not_allowed".equals(reason) ? CLOSE_ORIGIN_NOT_ALLOWED : CLOSE_POLICY;
  }

  private static final int MAX_AUTH_FRAME_CHARS = 4096;
  private static final int MAX_TOKEN_CHARS = 2048;

  private final ConcurrentHashMap<String, PendingSession> pending = new ConcurrentHashMap<>();
  private final ConcurrentHashMap.KeySetView<String, Boolean> optionalAuthenticated = ConcurrentHashMap.newKeySet();
  private final AtomicInteger pendingCount = new AtomicInteger();
  private final int maximumPending;
  private final long timeoutNanos;
  private final Verifier scopeVerifier;
  private final LongSupplier nanoTime;

  public WsAuthenticationGate(
      int maximumPending,
      long timeoutMillis,
      Verifier scopeVerifier
  ) {
    this(maximumPending, timeoutMillis, scopeVerifier, System::nanoTime);
  }

  public WsAuthenticationGate(
      int maximumPending,
      long timeoutMillis,
      Verifier scopeVerifier,
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

  public <C extends WsChannel> boolean register(
      C channel,
      Consumer<String> policyCloser,
      String requiredScope,
      WebSocketSessions<C> targetSessions
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
        tokenId -> targetSessions.add(channel, tokenId),
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
      WebSocketSessions<?> targetSessions,
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
    if (frame == null) {
      session.policyCloser().accept("Invalid authentication frame");
      return false;
    }
    String refusal = scopeVerifier.refusal(frame.token(), session.requiredScope());
    if (refusal != null) {
      session.policyCloser().accept(refusal);
      return false;
    }
    if (!session.channel().isOpen()) {
      session.policyCloser().accept("Connection closed during authentication");
      return false;
    }
    session.admit().accept(tokenIdOf(frame.token()));
    return true;
  }

  public boolean authenticateOptional(
      String sessionId,
      String message,
      String requiredScope,
      WebSocketSessions<?> targetSessions,
      Consumer<String> policyCloser
  ) {
    Objects.requireNonNull(sessionId, "sessionId");
    Objects.requireNonNull(requiredScope, "requiredScope");
    Objects.requireNonNull(targetSessions, "targetSessions");
    Objects.requireNonNull(policyCloser, "policyCloser");

    AuthFrame frame = parse(message);
    String refusal = frame == null ? "Invalid authentication frame" : scopeVerifier.refusal(frame.token(), requiredScope);
    if (!optionalAuthenticated.add(sessionId) || refusal != null) {
      targetSessions.remove(sessionId);
      policyCloser.accept(refusal == null ? "Repeated authentication frame" : refusal);
      return false;
    }
    targetSessions.bindToken(sessionId, tokenIdOf(frame.token()));
    return true;
  }

  public void reject(
      String sessionId,
      WebSocketSessions<?> targetSessions,
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

  static String tokenIdOf(String bearer) {
    int dot = bearer.indexOf('.');
    return dot > 0 ? bearer.substring(0, dot) : bearer;
  }

  private record AuthFrame(String token) {
  }

  private record PendingSession(
      WsChannel channel,
      Consumer<String> policyCloser,
      String requiredScope,
      WebSocketSessions<?> targetSessions,
      Consumer<String> admit,
      long deadlineNanos
  ) {
  }
}
