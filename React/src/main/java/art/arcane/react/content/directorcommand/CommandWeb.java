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
import art.arcane.react.util.director.handlers.StringHandler;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.collection.KList;
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
      @Param(
          name = "role",
          description = "Role for this token: viewer, operator, or admin",
          descriptionKey = "command.parameter.web.role",
          defaultValue = "viewer",
          aliases = {"r"},
          customHandler = WebRoleHandler.class
      )
      String role
  ) {
    WebRole webRole = WebRole.fromId(role);
    if (webRole == null) {
      ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_INVALID_ROLE, MessageArgument.untrusted("role", role));
      return;
    }
    WebController wc = React.controller(WebController.class);
    String unavailableReason = wc.pairingUnavailableReason();
    if (unavailableReason != null) {
      ReactLanguage.sendPrefixed(
          sender(),
          CommandMessages.WEB_PAIR_UNAVAILABLE,
          MessageArgument.untrusted("reason", unavailableReason)
      );
      return;
    }

    PairingMaterial material;
    try {
      material = createPairing(wc, label, webRole);
    } catch (IllegalArgumentException e) {
      ReactLanguage.sendPrefixed(
          sender(),
          CommandMessages.WEB_PAIR_UNAVAILABLE,
          MessageArgument.untrusted("reason", e.getMessage())
      );
      return;
    } catch (IOException e) {
      ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_PERSIST_FAILED, MessageArgument.untrusted("reason", e.getMessage()));
      return;
    } catch (IllegalStateException e) {
      String currentUnavailableReason = wc.pairingUnavailableReason();
      if (currentUnavailableReason != null) {
        ReactLanguage.sendPrefixed(
            sender(),
            CommandMessages.WEB_PAIR_UNAVAILABLE,
            MessageArgument.untrusted("reason", currentUnavailableReason)
        );
        return;
      }
      React.reportError(e);
      ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_PERSIST_FAILED, MessageArgument.untrusted("reason", e.getMessage()));
      return;
    }
    auditLog().append(sender().getName(), "pair", "label=" + label + " role=" + webRole.id(), "OK");
    sendPairingCode(material.code());
    ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_SERVER_FINGERPRINT, MessageArgument.untrusted("fingerprint", material.serverFingerprint()));
    ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_TOKEN_FINGERPRINT, MessageArgument.untrusted("fingerprint", material.tokenFingerprint()));
    ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_TOKEN_ID, MessageArgument.untrusted("id", material.tokenId()));
    ReactLanguage.sendPrefixed(sender(), CommandMessages.WEB_ROLE, MessageArgument.untrusted("role", webRole.id()));
  }

  static PairingMaterial createPairing(WebController controller, String label, WebRole role) throws IOException {
    synchronized (controller) {
      return createPairingWhileBound(controller, label, role);
    }
  }

  private static PairingMaterial createPairingWhileBound(
      WebController controller,
      String label,
      WebRole role
  ) throws IOException {
    String directUrl = controller.resolveDirectUrl();
    controller.loadAuth();
    ReactServerIdentity identity = controller.getIdentity();
    byte[] secret = controller.getSecret();
    TokenStore store = controller.getTokenStore();
    String tokenId = generateTokenId();
    long issuedAt = System.currentTimeMillis();
    Set<String> scopes = role.scopes();
    String bearer = PairingToken.mint(secret, tokenId, label, issuedAt, scopes);
    int dotPosition = bearer.indexOf('.');
    String tokenSignature = dotPosition >= 0 ? bearer.substring(dotPosition + 1) : bearer;
    String relayUrl = controller.getConfig().isRelayEnabled() ? controller.getConfig().getRelayUrl() : "";
    String code = PairingCode.encode(
        directUrl,
        relayUrl,
        identity.publicKeyBase64(),
        identity.fingerprint(),
        tokenId,
        tokenSignature
    );
    TokenRecord record = new TokenRecord(tokenId, label, issuedAt, scopes, role.id());
    store.add(record);
    try {
      store.save(controller.tokensFile());
    } catch (IOException failure) {
      store.remove(tokenId);
      throw failure;
    }
    return new PairingMaterial(
        code,
        identity.fingerprint(),
        sha256Hex(bearer),
        tokenId
    );
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

  public static final class WebRoleHandler extends StringHandler {
    @Override
    public KList<String> getPossibilities() {
      KList<String> roles = new KList<>();
      for (WebRole role : WebRole.values()) {
        roles.add(role.id());
      }
      return roles;
    }
  }

  record PairingMaterial(
      String code,
      String serverFingerprint,
      String tokenFingerprint,
      String tokenId
  ) {
  }
}
