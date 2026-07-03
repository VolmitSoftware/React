package art.arcane.react.web;

import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.TokenRecord;
import art.arcane.react.api.web.TokenStore;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PairingTokenTest {

  @Test
  void mintedTokenVerifiesAndTamperFails() {
    byte[] secret = new byte[32];
    new SecureRandom().nextBytes(secret);
    String t = PairingToken.mint(secret, "abc", "laptop", 1000L, Set.of("read", "op:execute"));
    TokenStore store = TokenStore.inMemory(new TokenRecord("abc", "laptop", 1000L, Set.of("read", "op:execute"), "operator"));
    assertTrue(PairingToken.verify(secret, t, store).orElseThrow().hasScope("op:execute"));
    assertTrue(PairingToken.verify(secret, t + "x", store).isEmpty());
    byte[] other = new byte[32];
    new SecureRandom().nextBytes(other);
    assertTrue(PairingToken.verify(other, t, store).isEmpty());
  }

  @Test
  void fromTomlLoadsTokenRecord() throws IOException {
    Path dir = Files.createTempDirectory("tokenstore-test");
    File tomlFile = dir.resolve("tokens.toml").toFile();
    Files.writeString(tomlFile.toPath(),
        "[[tokens]]\nid = \"tok1\"\nlabel = \"desktop\"\nissuedAt = 2000\nscopes = [\"read\"]\n");
    TokenStore store = TokenStore.fromToml(tomlFile);
    assertTrue(store.lookup("tok1").isPresent());
    assertEquals("desktop", store.lookup("tok1").orElseThrow().label());
    assertEquals(2000L, store.lookup("tok1").orElseThrow().issuedAt());
    assertTrue(store.lookup("tok1").orElseThrow().scopes().contains("read"));
    assertNull(store.lookup("tok1").orElseThrow().role(), "legacy token without role line must have null role");
    assertTrue(store.lookup("missing").isEmpty());
    File absent = dir.resolve("nonexistent.toml").toFile();
    assertTrue(TokenStore.fromToml(absent).lookup("tok1").isEmpty());

    File roleTomlFile = dir.resolve("tokens-with-role.toml").toFile();
    Files.writeString(roleTomlFile.toPath(),
        "[[tokens]]\nid = \"tok2\"\nlabel = \"laptop\"\nissuedAt = 3000\nscopes = [\"read\"]\nrole = \"viewer\"\n");
    TokenStore storeWithRole = TokenStore.fromToml(roleTomlFile);
    assertTrue(storeWithRole.lookup("tok2").isPresent());
    assertEquals("viewer", storeWithRole.lookup("tok2").orElseThrow().role(), "token with role line must load role");
  }

  @Test
  void viewerRecordYieldsViewerRoleAndReadScope() {
    byte[] secret = new byte[32];
    new SecureRandom().nextBytes(secret);
    TokenRecord rec = new TokenRecord("v1", "viewer-device", 1000L, Set.of("read"), "viewer");
    TokenStore store = TokenStore.inMemory(rec);
    String bearer = PairingToken.mint(secret, rec.id(), rec.label(), rec.issuedAt(), rec.scopes());
    PairingToken token = PairingToken.verify(secret, bearer, store).orElseThrow();
    assertEquals("viewer", token.role());
    assertTrue(token.hasScope("read"));
    assertFalse(token.hasScope("op:execute"));
    assertFalse(token.hasScope("admin"));
    assertEquals(List.of("read"), token.scopes());
  }

  @Test
  void operatorRecordYieldsOperatorRoleAndExecuteScope() {
    byte[] secret = new byte[32];
    new SecureRandom().nextBytes(secret);
    TokenRecord rec = new TokenRecord("op1", "op-device", 1000L, Set.of("read", "op:execute"), "operator");
    TokenStore store = TokenStore.inMemory(rec);
    String bearer = PairingToken.mint(secret, rec.id(), rec.label(), rec.issuedAt(), rec.scopes());
    PairingToken token = PairingToken.verify(secret, bearer, store).orElseThrow();
    assertEquals("operator", token.role());
    assertTrue(token.hasScope("op:execute"));
    assertFalse(token.hasScope("admin"));
  }

  @Test
  void adminRecordYieldsAdminRoleAndAllScopes() {
    byte[] secret = new byte[32];
    new SecureRandom().nextBytes(secret);
    TokenRecord rec = new TokenRecord("adm1", "admin-device", 1000L, Set.of("read", "op:execute", "admin"), "admin");
    TokenStore store = TokenStore.inMemory(rec);
    String bearer = PairingToken.mint(secret, rec.id(), rec.label(), rec.issuedAt(), rec.scopes());
    PairingToken token = PairingToken.verify(secret, bearer, store).orElseThrow();
    assertEquals("admin", token.role());
    assertTrue(token.hasScope("admin"));
    assertEquals(List.of("admin", "op:execute", "read"), token.scopes());
  }

  @Test
  void legacyNullRoleResolvesToAdmin() {
    byte[] secret = new byte[32];
    new SecureRandom().nextBytes(secret);
    TokenRecord rec = new TokenRecord("leg1", "legacy-device", 1000L, Set.of("read", "op:execute"), null);
    TokenStore store = TokenStore.inMemory(rec);
    String bearer = PairingToken.mint(secret, rec.id(), rec.label(), rec.issuedAt(), rec.scopes());
    PairingToken token = PairingToken.verify(secret, bearer, store).orElseThrow();
    assertEquals("admin", token.role());
    assertTrue(token.hasScope("admin"));
  }
}
