package art.arcane.react.web;

import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.TokenRecord;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.WebAuth;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HandlerType;
import io.javalin.http.UnauthorizedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WebAuthTest {

  private byte[] secret;
  private TokenRecord readRecord;
  private TokenStore store;
  private String readBearer;
  private TokenRecord writeRecord;
  private String writeBearer;

  @BeforeEach
  void setUp() {
    secret = new byte[32];
    new SecureRandom().nextBytes(secret);
    readRecord = new TokenRecord("tok-read", "read-device", 1000L, Set.of("read"), "viewer");
    writeRecord = new TokenRecord("tok-write", "write-device", 1000L, Set.of("read", "op:execute"), "operator");
    store = TokenStore.inMemory(readRecord, writeRecord);
    readBearer = PairingToken.mint(secret, readRecord.id(), readRecord.label(), readRecord.issuedAt(), readRecord.scopes());
    writeBearer = PairingToken.mint(secret, writeRecord.id(), writeRecord.label(), writeRecord.issuedAt(), writeRecord.scopes());
  }

  @Test
  void missingAuthorizationHeaderIsUnauthorized() {
    WebAuth auth = new WebAuth(secret, store);
    Context ctx = mock(Context.class);
    when(ctx.method()).thenReturn(HandlerType.GET);
    when(ctx.header("Authorization")).thenReturn(null);
    assertThrows(UnauthorizedResponse.class, () -> auth.handle(ctx));
  }

  @Test
  void optionsPreflightSkipsAuth() throws Exception {
    WebAuth auth = new WebAuth(secret, store);
    Context ctx = mock(Context.class);
    when(ctx.method()).thenReturn(HandlerType.OPTIONS);
    auth.handle(ctx);
    verify(ctx, never()).attribute(eq("token"), any(PairingToken.class));
  }

  @Test
  void getWithoutCounterPasses() throws Exception {
    WebAuth auth = new WebAuth(secret, store);
    Context ctx = mock(Context.class);
    when(ctx.method()).thenReturn(HandlerType.GET);
    when(ctx.header("Authorization")).thenReturn("Bearer " + readBearer);
    when(ctx.header("X-React-Counter")).thenReturn(null);
    auth.handle(ctx);
    verify(ctx).attribute(eq("token"), any(PairingToken.class));
  }

  @Test
  void getIgnoresCounterEvenIfPresent() throws Exception {
    WebAuth auth = new WebAuth(secret, store);

    Context ctx1 = mock(Context.class);
    when(ctx1.method()).thenReturn(HandlerType.GET);
    when(ctx1.header("Authorization")).thenReturn("Bearer " + readBearer);
    when(ctx1.header("X-React-Counter")).thenReturn("5");
    auth.handle(ctx1);
    verify(ctx1).attribute(eq("token"), any(PairingToken.class));

    Context ctx2 = mock(Context.class);
    when(ctx2.method()).thenReturn(HandlerType.GET);
    when(ctx2.header("Authorization")).thenReturn("Bearer " + readBearer);
    when(ctx2.header("X-React-Counter")).thenReturn("5");
    auth.handle(ctx2);
    verify(ctx2).attribute(eq("token"), any(PairingToken.class));
  }

  @Test
  void putWithoutCounterIsConflict() {
    WebAuth auth = new WebAuth(secret, store);
    Context ctx = mock(Context.class);
    when(ctx.method()).thenReturn(HandlerType.PUT);
    when(ctx.header("Authorization")).thenReturn("Bearer " + writeBearer);
    when(ctx.header("X-React-Counter")).thenReturn(null);
    assertThrows(ConflictResponse.class, () -> auth.handle(ctx));
  }

  @Test
  void putWithIncreasingCounterPassesReplayIsConflict() throws Exception {
    WebAuth auth = new WebAuth(secret, store);

    Context ctx1 = mock(Context.class);
    when(ctx1.method()).thenReturn(HandlerType.PUT);
    when(ctx1.header("Authorization")).thenReturn("Bearer " + writeBearer);
    when(ctx1.header("X-React-Counter")).thenReturn("5");
    auth.handle(ctx1);
    verify(ctx1).attribute(eq("token"), any(PairingToken.class));

    Context ctx2 = mock(Context.class);
    when(ctx2.method()).thenReturn(HandlerType.PUT);
    when(ctx2.header("Authorization")).thenReturn("Bearer " + writeBearer);
    when(ctx2.header("X-React-Counter")).thenReturn("5");
    assertThrows(ConflictResponse.class, () -> auth.handle(ctx2));

    Context ctx3 = mock(Context.class);
    when(ctx3.method()).thenReturn(HandlerType.PUT);
    when(ctx3.header("Authorization")).thenReturn("Bearer " + writeBearer);
    when(ctx3.header("X-React-Counter")).thenReturn("6");
    auth.handle(ctx3);
    verify(ctx3).attribute(eq("token"), any(PairingToken.class));
  }

  @Test
  void putWithNonNumericCounterIsBadRequest() {
    WebAuth auth = new WebAuth(secret, store);
    Context ctx = mock(Context.class);
    when(ctx.method()).thenReturn(HandlerType.PUT);
    when(ctx.header("Authorization")).thenReturn("Bearer " + writeBearer);
    when(ctx.header("X-React-Counter")).thenReturn("abc");
    assertThrows(BadRequestResponse.class, () -> auth.handle(ctx));
  }

  @Test
  void readOnlyTokenRejectsExecuteScope() throws Exception {
    WebAuth auth = new WebAuth(secret, store);
    Context ctx = mock(Context.class);
    when(ctx.method()).thenReturn(HandlerType.GET);
    when(ctx.header("Authorization")).thenReturn("Bearer " + readBearer);
    when(ctx.header("X-React-Counter")).thenReturn(null);
    auth.handle(ctx);
    PairingToken token = PairingToken.verify(secret, readBearer, store).orElseThrow();
    when(ctx.<PairingToken>attribute("token")).thenReturn(token);
    assertThrows(ForbiddenResponse.class, () -> WebAuth.requireScope(ctx, "op:execute"));
  }
}
