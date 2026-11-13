package art.arcane.react.api.web.pairing;

import art.arcane.react.React;
import art.arcane.react.api.web.AuditLog;
import art.arcane.react.api.web.WebError;
import art.arcane.react.api.web.resource.PairResource;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageKey;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * What pairing attempts leave behind, as the Animus editor's: the audit log, one console line per repeated failure, and a
 * chat line to the player who made a link a browser could not use (its origin was refused, or the link expired).
 */
public final class PairingNotices implements PairResource.Events {
  private static final int REMEMBERED_KEYS = 1024;

  private final Supplier<WebTokens> tokens;
  private final Supplier<AuditLog> audit;
  private final Map<String, Long> said = new ConcurrentHashMap<>();

  public PairingNotices(Supplier<WebTokens> tokens, Supplier<AuditLog> audit) {
    this.tokens = tokens;
    this.audit = audit;
  }

  @Override
  public void paired(WebToken token, String address) {
    audit.get().append("web:" + token.id(), "pair", "client=" + token.client().orElse("") + " address=" + address, "paired");
    React.info("React Web paired " + token.id() + (token.label().isBlank() ? "" : " \"" + token.label() + "\"")
        + " as " + token.role() + token.client().map(client -> " from " + client).orElse(""));
  }

  @Override
  public void refused(String tokenId, WebError reason, String address) {
    audit.get().append("web:" + tokenId, "pair", "address=" + address, reason.code());
    if ("pairing_expired".equals(reason.code())) {
      if (due("pair " + tokenId, Duration.ofHours(1))) {
        React.warn("React Web pairing " + tokenId + " failed: the link expired before it was opened; run /react web pair again");
        WebTokens store = tokens.get();
        if (store != null) {
          store.link(tokenId).ifPresent(link -> tellIssuer(link, CommandMessages.WEB_REFUSED_EXPIRED));
        }
      }
      return;
    }
    if (due("pair from " + address, Duration.ofMinutes(1))) {
      React.warn("React Web pairing from " + address + " failed: " + reason.code());
    }
  }

  /** A browser page from an origin outside {@code allowedOrigins}: logged once an hour, told to whoever has a link open. */
  public void originRefused(String origin) {
    if (origin == null) {
      return;
    }
    String shown = origin.length() > 128 ? origin.substring(0, 128) : origin;
    if (due("origin " + shown, Duration.ofHours(1))) {
      React.warn("React Web refused origin " + shown + ": it is not in allowedOrigins in web.toml");
    }
    WebTokens store = tokens.get();
    if (store == null) {
      return;
    }
    for (WebTokens.OpenLink link : store.openLinks()) {
      if (due("origin notice " + shown + " " + link.tokenId(), Duration.ofHours(1))) {
        tellIssuer(link, CommandMessages.WEB_REFUSED_ORIGIN, MessageArgument.untrusted("origin", shown));
      }
    }
  }

  /** True once per {@code every} for {@code key}, so a repeated failure is said once. */
  private boolean due(String key, Duration every) {
    long now = System.nanoTime();
    if (said.size() > REMEMBERED_KEYS) {
      said.clear();
    }
    Long last = said.get(key);
    if (last != null && now - last < every.toNanos()) {
      return false;
    }
    said.put(key, now);
    return true;
  }

  /** The player who made the link, if online, gets one chat line; a link made from the console is only logged. */
  private static void tellIssuer(WebTokens.OpenLink link, MessageKey why, MessageArgument... arguments) {
    Optional<UUID> issuer = link.grant().issuerId();
    if (issuer.isEmpty() || React.instance == null) {
      return;
    }
    String who = link.grant().label().isBlank() ? link.tokenId() : link.grant().label();
    J.s(() -> {
      Player player = Bukkit.getPlayer(issuer.get());
      if (player == null) {
        return;
      }
      J.runEntity(player, () -> ReactLanguage.sendPrefixed(
          player,
          CommandMessages.WEB_PAIR_REFUSED,
          MessageArgument.untrusted("label", who),
          MessageArgument.untrusted("reason", ReactLanguage.plain(why, arguments))
      ));
    });
  }
}
