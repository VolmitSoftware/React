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
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.localization.MessageArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

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
    description = "Manage React WebUI access tokens",
    descriptionKey = "command.description.web"
)
public class CommandWeb implements DirectorExecutor {

  @Director(
      name = "pair",
      description = "Create a WebUI pairing token",
      descriptionKey = "command.description.web.pair"
  )
  public void pair(
      @Param(name = "label", description = "Human-readable label for this token", descriptionKey = "command.parameter.web.label")
      String label,
      @Param(name = "role", description = "Role for this token: viewer, operator, or admin", descriptionKey = "command.parameter.web.role", defaultValue = "viewer", aliases = {"r"})
      String role
  ) {
    WebRole webRole = WebRole.fromId(role);
    if (webRole == null) {
      ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_INVALID_ROLE, MessageArgument.untrusted("role", role));
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
    String directUrl = wc.resolveDirectUrl();
    String relayUrl = wc.getConfig().isRelayEnabled() ? wc.getConfig().getRelayUrl() : "";
    String code = PairingCode.encode(directUrl, relayUrl, id.publicKeyBase64(), id.fingerprint(), tokenId, tokenSig);
    String tokenFingerprint = sha256Hex(bearer);
    TokenRecord record = new TokenRecord(tokenId, label, issuedAt, scopes, webRole.id());
    store.add(record);
    try {
      store.save(wc.tokensFile());
    } catch (IOException e) {
      store.remove(tokenId);
      ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_PERSIST_FAILED, MessageArgument.untrusted("reason", e.getMessage()));
      return;
    }
    auditLog().append(sender().getName(), "pair", "label=" + label + " role=" + webRole.id(), "OK");
    sendPairingCode(code);
    ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_SERVER_FINGERPRINT, MessageArgument.untrusted("fingerprint", id.fingerprint()));
    ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_TOKEN_FINGERPRINT, MessageArgument.untrusted("fingerprint", tokenFingerprint));
    ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_TOKEN_ID, MessageArgument.untrusted("id", tokenId));
    ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_ROLE, MessageArgument.untrusted("role", webRole.id()));
  }

  @Director(
      name = "list",
      aliases = {"ls"},
      description = "List active WebUI tokens",
      descriptionKey = "command.description.web.list"
  )
  public void list() {
    WebController wc = React.controller(WebController.class);
    wc.loadAuth();
    List<TokenRecord> records = wc.getTokenStore().all();
    if (records.isEmpty()) {
      ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_NO_TOKENS);
      return;
    }
    ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_TOKEN_HEADER, MessageArgument.untrusted("count", records.size()));
    for (TokenRecord rec : records) {
      ReactLanguage.send(
          sender(),
          CommandMessages.WEB_TOKEN_ENTRY,
          MessageArgument.untrusted("id", rec.id()),
          MessageArgument.untrusted("label", rec.label()),
          MessageArgument.untrusted("issued_at", rec.issuedAt())
      );
    }
  }

  @Director(
      name = "revoke",
      description = "Revoke a WebUI token",
      descriptionKey = "command.description.web.revoke"
  )
  public void revoke(
      @Param(name = "id", description = "Token ID to revoke", descriptionKey = "command.parameter.web.id")
      String id
  ) {
    WebController wc = React.controller(WebController.class);
    wc.loadAuth();
    TokenStore store = wc.getTokenStore();
    if (store.lookup(id).isEmpty()) {
      ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_TOKEN_NOT_FOUND, MessageArgument.untrusted("id", id));
      return;
    }
    store.remove(id);
    try {
      store.save(wc.tokensFile());
    } catch (IOException e) {
      ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_UPDATE_FAILED, MessageArgument.untrusted("reason", e.getMessage()));
      return;
    }
    auditLog().append(sender().getName(), "revoke", "id=" + id, "OK");
    ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_REVOKED, MessageArgument.untrusted("id", id));
  }

  private static AuditLog auditLog() {
    return new AuditLog(React.instance.getDataFolder());
  }

  static Component playerPairingCodeComponent(String code) {
    return ReactLanguage.prefixedComponent(CommandMessages.WEB_PAIRING_COPY)
        .clickEvent(ClickEvent.copyToClipboard(code))
        .hoverEvent(HoverEvent.showText(ReactLanguage.component(CommandMessages.WEB_PAIRING_COPY_HOVER)));
  }

  static Component consolePairingCodeComponent(String code) {
    return ReactLanguage.prefixedComponent(
        CommandMessages.WEB_PAIRING_CODE,
        MessageArgument.untrusted("code", code)
    );
  }

  private void sendPairingCode(String code) {
    VolmitSender commandSender = sender();
    if (!commandSender.isPlayer()) {
      commandSender.sendComponent(consolePairingCodeComponent(code));
      return;
    }
    commandSender.sendComponent(playerPairingCodeComponent(code));
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
