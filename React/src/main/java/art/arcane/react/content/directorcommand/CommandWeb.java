package art.arcane.react.content.directorcommand;

import art.arcane.react.React;
import art.arcane.react.api.web.AuditLog;
import art.arcane.react.api.web.WebRole;
import art.arcane.react.api.web.pairing.WebToken;
import art.arcane.react.api.web.pairing.WebTokens;
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
import art.arcane.volmlib.util.localization.TextKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /react web pair|status|tokens|revoke}, the Animus editor's pairing commands under React's {@code web} parent.
 * {@code pair} takes the keyed options {@code label=}, {@code role=} or {@code scopes=a,b}, and {@code expires=7d}.
 */
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
      description = "Make a one-time React Web pairing link",
      descriptionKey = "command.description.web.pair"
  )
  public void pair(
      @Param(name = "label", description = "Human-readable label for this token", descriptionKey = "command.parameter.web.label", defaultValue = "")
      String label,
      @Param(
          name = "role",
          description = "Role for this token: viewer, operator, or admin",
          descriptionKey = "command.parameter.web.role",
          defaultValue = "",
          aliases = {"r"},
          customHandler = WebRoleHandler.class
      )
      String role,
      @Param(
          name = "scopes",
          description = "Scopes instead of a role, such as read,op:execute",
          descriptionKey = "command.parameter.web.scopes",
          defaultValue = "",
          customHandler = WebScopesHandler.class
      )
      String scopes,
      @Param(
          name = "expires",
          description = "How long the pairing lasts without use, such as 7d",
          descriptionKey = "command.parameter.web.expires",
          defaultValue = "",
          customHandler = WebExpiresHandler.class
      )
      String expires
  ) {
    WebController wc = React.controller(WebController.class);
    VolmitSender sender = sender();
    WebTokens.Grant grant;
    try {
      grant = grant(label, role, scopes, expires, wc.getConfig().getTokenDays(), sender.getName(),
          sender.isPlayer() ? Optional.of(sender.player().getUniqueId()) : Optional.empty());
    } catch (OptionRefused refused) {
      ReactLanguage.sendPrefixed(sender, refused.key, refused.arguments);
      return;
    }
    WebController.IssuedLink issued;
    try {
      issued = wc.createLink(grant);
    } catch (IllegalStateException unavailable) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.WEB_PAIR_UNAVAILABLE, MessageArgument.untrusted("reason", unavailable.getMessage()));
      return;
    }
    if (sender.isPlayer()) {
      sender.sendComponent(playerReply(issued.url(), issued.link().tokenId()));
      return;
    }
    consoleReply(issued.url(), grant, issued.link().tokenId()).forEach(sender::sendComponent);
  }

  @Director(
      name = "status",
      description = "Show the listener, relay and fingerprint",
      descriptionKey = "command.description.web.status"
  )
  public void status() {
    WebController.WebStatus status = React.controller(WebController.class).status();
    VolmitSender sender = sender();
    String relay = switch (status.relay()) {
      case OFF -> ReactLanguage.plain(CommandMessages.WEB_OFF);
      case WAITING -> ReactLanguage.plain(CommandMessages.WEB_RELAY_WAITING);
      case CONNECTING -> ReactLanguage.plain(CommandMessages.WEB_RELAY_CONNECTING, MessageArgument.untrusted("url", status.relayUrl()));
      case REGISTERED -> ReactLanguage.plain(CommandMessages.WEB_RELAY_REGISTERED, MessageArgument.untrusted("url", status.relayUrl()));
    };
    ReactLanguage.sendPrefixed(
        sender,
        CommandMessages.WEB_STATUS,
        MessageArgument.untrusted("state", status.problem() == null ? ReactLanguage.plain(CommandMessages.WEB_STATE_READY) : status.problem()),
        MessageArgument.untrusted("listener", status.listener().isEmpty() ? ReactLanguage.plain(CommandMessages.WEB_OFF) : status.listener()),
        MessageArgument.untrusted("relay", relay),
        MessageArgument.untrusted("tokens", status.tokens()),
        MessageArgument.untrusted("links", status.links()),
        MessageArgument.untrusted("sockets", status.sockets())
    );
    Component fingerprint = ReactLanguage.prefixedComponent(
        CommandMessages.WEB_STATUS_FINGERPRINT,
        MessageArgument.untrusted("fingerprint", status.fingerprint())
    );
    sender.sendComponent(sender.isPlayer()
        ? fingerprint.clickEvent(ClickEvent.copyToClipboard(status.fingerprint()))
        .hoverEvent(HoverEvent.showText(ReactLanguage.component(CommandMessages.WEB_FINGERPRINT_COPY_HOVER)))
        : fingerprint);
  }

  @Director(
      name = "tokens",
      description = "List paired browsers",
      descriptionKey = "command.description.web.tokens"
  )
  public void tokens() {
    WebController wc = React.controller(WebController.class);
    wc.loadAuth();
    List<WebToken> tokens = wc.getTokens().list();
    VolmitSender sender = sender();
    if (tokens.isEmpty()) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.WEB_NO_TOKENS);
      return;
    }
    ReactLanguage.sendPrefixed(sender, CommandMessages.WEB_TOKEN_HEADER, MessageArgument.untrusted("count", tokens.size()));
    for (WebToken token : tokens) {
      ReactLanguage.send(
          sender,
          CommandMessages.WEB_TOKEN_ENTRY,
          MessageArgument.untrusted("id", token.id()),
          MessageArgument.untrusted("label", token.label().isBlank() ? "-" : token.label()),
          MessageArgument.untrusted("access", access(token.scopes())),
          MessageArgument.untrusted("expires", token.expires().truncatedTo(ChronoUnit.SECONDS).toString()),
          MessageArgument.untrusted("used", token.lastUsed().map(Instant::toString).orElse(ReactLanguage.plain(CommandMessages.WEB_NEVER))),
          MessageArgument.untrusted("client", token.client().orElse("-")),
          MessageArgument.untrusted("sockets", wc.socketsOf(token.id()))
      );
    }
  }

  @Director(
      name = "revoke",
      description = "Revoke a token, or cancel a link not opened yet",
      descriptionKey = "command.description.web.revoke"
  )
  public void revoke(
      @Param(name = "id", description = "Token id, or all", descriptionKey = "command.parameter.web.id", customHandler = WebTokenHandler.class)
      String id
  ) {
    WebController wc = React.controller(WebController.class);
    VolmitSender sender = sender();
    List<String> revoked;
    try {
      revoked = wc.revoke(id);
    } catch (IOException e) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.WEB_UPDATE_FAILED, MessageArgument.untrusted("reason", e.getMessage()));
      return;
    }
    if (revoked.isEmpty()) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.WEB_TOKEN_NOT_FOUND, MessageArgument.untrusted("id", id));
      return;
    }
    new AuditLog(React.instance.getDataFolder()).append(sender.getName(), "revoke", "ids=" + String.join(",", revoked), "OK");
    ReactLanguage.sendPrefixed(sender, CommandMessages.WEB_REVOKED, MessageArgument.untrusted("id", String.join(", ", revoked)));
  }

  /** Reads the pairing options; a blank option keeps its default. */
  static WebTokens.Grant grant(String label, String role, String scopes, String expires, int tokenDays, String issuer,
                               Optional<UUID> issuerId) throws OptionRefused {
    String name = label == null ? "" : label.strip();
    if (name.length() > 64) {
      throw new OptionRefused(CommandMessages.WEB_INVALID_LABEL);
    }
    boolean hasRole = role != null && !role.isBlank();
    boolean hasScopes = scopes != null && !scopes.isBlank();
    if (hasRole && hasScopes) {
      throw new OptionRefused(CommandMessages.WEB_ROLE_AND_SCOPES);
    }
    List<String> granted = WebRole.DEFAULT.scopes();
    if (hasRole) {
      WebRole webRole = WebRole.fromId(role);
      if (webRole == null) {
        throw new OptionRefused(CommandMessages.WEB_INVALID_ROLE, MessageArgument.untrusted("role", role));
      }
      granted = webRole.scopes();
    }
    if (hasScopes) {
      try {
        granted = WebRole.parseScopes(scopes);
      } catch (IllegalArgumentException unknown) {
        throw new OptionRefused(CommandMessages.WEB_INVALID_SCOPE, MessageArgument.untrusted("scope", unknown.getMessage()),
            MessageArgument.untrusted("scopes", String.join(", ", WebRole.SCOPES)));
      }
      if (!granted.contains("read")) {
        throw new OptionRefused(CommandMessages.WEB_SCOPES_NEED_READ);
      }
    }
    Duration lifetime;
    if (expires == null || expires.isBlank()) {
      lifetime = Duration.ofDays(tokenDays);
    } else {
      lifetime = duration(expires.strip()).orElseThrow(() -> new OptionRefused(CommandMessages.WEB_INVALID_EXPIRES));
    }
    if (lifetime.compareTo(WebTokens.MINIMUM_LIFETIME) < 0 || lifetime.compareTo(WebTokens.MAXIMUM_LIFETIME) > 0) {
      throw new OptionRefused(CommandMessages.WEB_INVALID_EXPIRES);
    }
    return new WebTokens.Grant(name, granted, lifetime, issuer, issuerId);
  }

  static Optional<Duration> duration(String text) {
    if (!text.matches("[1-9][0-9]{0,5}[dhm]")) {
      return Optional.empty();
    }
    long amount = Long.parseLong(text.substring(0, text.length() - 1));
    return Optional.of(switch (text.charAt(text.length() - 1)) {
      case 'd' -> Duration.ofDays(amount);
      case 'h' -> Duration.ofHours(amount);
      default -> Duration.ofMinutes(amount);
    });
  }

  static String describe(Duration lifetime) {
    if (lifetime.toDays() > 0 && lifetime.equals(Duration.ofDays(lifetime.toDays()))) {
      return lifetime.toDays() + "d";
    }
    if (lifetime.toHours() > 0 && lifetime.equals(Duration.ofHours(lifetime.toHours()))) {
      return lifetime.toHours() + "h";
    }
    return lifetime.toMinutes() + "m";
  }

  /** The role name when the scopes match one, else the scopes. */
  static String access(List<String> scopes) {
    String role = WebRole.roleOf(scopes);
    return WebRole.CUSTOM.equals(role) ? String.join(",", scopes) : role;
  }

  /** One line: "Link ready. Open it within 10 minutes" with [Open React Web], [Copy] and [Revoke]. */
  static Component playerReply(String url, String tokenId) {
    return ReactLanguage.prefixedComponent(CommandMessages.WEB_LINK_READY)
        .append(Component.space())
        .append(ReactLanguage.component(CommandMessages.WEB_LINK_OPEN)
            .clickEvent(ClickEvent.openUrl(url))
            .hoverEvent(HoverEvent.showText(ReactLanguage.component(CommandMessages.WEB_LINK_OPEN_HOVER))))
        .append(Component.space())
        .append(ReactLanguage.component(CommandMessages.WEB_LINK_COPY)
            .clickEvent(ClickEvent.copyToClipboard(url))
            .hoverEvent(HoverEvent.showText(ReactLanguage.component(CommandMessages.WEB_LINK_COPY_HOVER))))
        .append(Component.space())
        .append(ReactLanguage.component(CommandMessages.WEB_LINK_REVOKE)
            .clickEvent(ClickEvent.suggestCommand("/react web revoke " + tokenId))
            .hoverEvent(HoverEvent.showText(ReactLanguage.component(CommandMessages.WEB_LINK_REVOKE_HOVER))));
  }

  /** The link itself, then the scopes, lifetime and token id. */
  static List<Component> consoleReply(String url, WebTokens.Grant grant, String tokenId) {
    String role = WebRole.roleOf(grant.scopes());
    String scopes = String.join(" ", grant.scopes()) + (WebRole.CUSTOM.equals(role) ? "" : " (" + role + ")");
    return List.of(
        ReactLanguage.prefixedComponent(
            CommandMessages.WEB_LINK_READY_CONSOLE,
            MessageArgument.untrusted("link", url)
        ),
        ReactLanguage.prefixedComponent(
            CommandMessages.WEB_LINK_DETAILS,
            MessageArgument.untrusted("scopes", scopes),
            MessageArgument.untrusted("lifetime", describe(grant.lifetime())),
            MessageArgument.untrusted("id", tokenId)
        )
    );
  }

  /** A pairing option the command cannot use, with the message that says why. */
  static final class OptionRefused extends Exception {
    final TextKey key;
    final MessageArgument[] arguments;

    OptionRefused(TextKey key, MessageArgument... arguments) {
      super(key.id(), null, false, false);
      this.key = key;
      this.arguments = arguments;
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

  public static final class WebScopesHandler extends StringHandler {
    @Override
    public KList<String> getPossibilities() {
      KList<String> scopes = new KList<>();
      scopes.addAll(WebRole.SCOPES);
      scopes.add(String.join(",", WebRole.OPERATOR.scopes()));
      return scopes;
    }
  }

  public static final class WebExpiresHandler extends StringHandler {
    @Override
    public KList<String> getPossibilities() {
      KList<String> lifetimes = new KList<>();
      lifetimes.addAll(List.of("1h", "12h", "1d", "7d", "30d"));
      return lifetimes;
    }
  }

  public static final class WebTokenHandler extends StringHandler {
    @Override
    public KList<String> getPossibilities() {
      KList<String> ids = new KList<>();
      WebController wc = React.instance == null ? null : React.controller(WebController.class);
      WebTokens tokens = wc == null ? null : wc.getTokens();
      if (tokens != null) {
        tokens.list().forEach(token -> ids.add(token.id()));
        tokens.openLinks().forEach(link -> ids.add(link.tokenId()));
      }
      ids.add("all");
      return ids;
    }
  }
}
