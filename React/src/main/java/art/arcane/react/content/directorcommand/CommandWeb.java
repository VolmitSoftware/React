package art.arcane.react.content.directorcommand;

import art.arcane.react.React;
import art.arcane.react.api.web.AuditLog;
import art.arcane.react.api.web.PairingCode;
import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.TokenRecord;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.WebRole;
import art.arcane.react.api.web.relay.ReactServerIdentity;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.format.C;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Set;

@Director(
    name = "web",
    aliases = {"w"},
    origin = DirectorOrigin.BOTH,
    description = "React web API token management."
)
public class CommandWeb implements DirectorExecutor {

  @Director(
      name = "pair",
      description = "Mint a bearer token and print a pairing code."
  )
  public void pair(
      @Param(name = "label", description = "Human-readable label for this token.")
      String label,
      @Param(name = "role", description = "Role for this token: viewer, operator, or admin.", defaultValue = "admin", aliases = {"r"})
      String role
  ) {
    WebRole webRole = WebRole.fromId(role);
    if (webRole == null) {
      sender().sendMessage(C.RED + "Invalid role: " + role + " (expected viewer, operator, or admin)");
      return;
    }
    WebController wc = React.controller(WebController.class);
    wc.loadAuth();
    ReactServerIdentity id = wc.getIdentity();
    byte[] secret = wc.getSecret();
    TokenStore store = wc.getTokenStore();
    String tokenId = generateTokenId();
    long issuedAt = System.currentTimeMillis();
    Set<String> scopes = webRole.scopes();
    String bearer = PairingToken.mint(secret, tokenId, label, issuedAt, scopes);
    int dotPos = bearer.indexOf('.');
    String tokenSig = dotPos >= 0 ? bearer.substring(dotPos + 1) : bearer;
    int confirmNum = new SecureRandom().nextInt(900000) + 100000;
    String confirmWord = String.valueOf(confirmNum);
    String host = wc.getConfig().getBindAddress();
    int port = wc.getConfig().getPort();
    String relayUrl = wc.getConfig().isRelayEnabled() ? wc.getConfig().getRelayUrl() : "";
    String code = PairingCode.encode(host, port, tokenId, tokenSig, confirmWord, relayUrl, id.publicKeyBase64(), id.fingerprint());
    String tokenFingerprint = sha256Hex(bearer);
    TokenRecord record = new TokenRecord(tokenId, label, issuedAt, scopes, webRole.id());
    store.add(record);
    try {
      store.save(wc.tokensFile());
    } catch (IOException e) {
      store.remove(tokenId);
      sender().sendMessage(C.RED + "Failed to persist token: " + e.getMessage());
      return;
    }
    auditLog().append(sender().getName(), "pair", "label=" + label + " role=" + webRole.id(), "OK");
    sender().sendMessage(C.REACT + "Pairing code: " + code);
    sender().sendMessage(C.REACT + "Server FP:     " + id.fingerprint());
    sender().sendMessage(C.REACT + "Token FP:      " + tokenFingerprint);
    sender().sendMessage(C.REACT + "Confirm:      " + confirmWord);
    sender().sendMessage(C.REACT + "Token ID:     " + tokenId);
    sender().sendMessage(C.REACT + "Role:         " + webRole.id());
  }

  @Director(
      name = "list",
      aliases = {"ls"},
      description = "List active bearer tokens."
  )
  public void list() {
    WebController wc = React.controller(WebController.class);
    wc.loadAuth();
    List<TokenRecord> records = wc.getTokenStore().all();
    if (records.isEmpty()) {
      sender().sendMessage(C.REACT + "No active tokens.");
      return;
    }
    sender().sendMessage(C.REACT + "Active tokens (" + records.size() + "):");
    for (TokenRecord rec : records) {
      sender().sendMessage(C.GRAY + "  " + rec.id() + " | " + rec.label() + " | issuedAt=" + rec.issuedAt());
    }
  }

  @Director(
      name = "revoke",
      description = "Revoke a bearer token by ID."
  )
  public void revoke(
      @Param(name = "id", description = "Token ID to revoke.")
      String id
  ) {
    WebController wc = React.controller(WebController.class);
    wc.loadAuth();
    TokenStore store = wc.getTokenStore();
    if (store.lookup(id).isEmpty()) {
      sender().sendMessage(C.RED + "Token not found: " + id);
      return;
    }
    store.remove(id);
    try {
      store.save(wc.tokensFile());
    } catch (IOException e) {
      sender().sendMessage(C.RED + "Failed to update token store: " + e.getMessage());
      return;
    }
    auditLog().append(sender().getName(), "revoke", "id=" + id, "OK");
    sender().sendMessage(C.REACT + "Revoked token: " + id);
  }

  private static AuditLog auditLog() {
    return new AuditLog(React.instance.getDataFolder());
  }

  private static String generateTokenId() {
    byte[] bytes = new byte[12];
    new SecureRandom().nextBytes(bytes);
    StringBuilder sb = new StringBuilder("rct-");
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
