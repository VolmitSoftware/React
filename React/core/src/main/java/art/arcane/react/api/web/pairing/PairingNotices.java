package art.arcane.react.api.web.pairing;

import art.arcane.react.api.web.AuditLog;
import art.arcane.react.api.web.WebError;
import art.arcane.react.api.web.WebLog;
import art.arcane.react.api.web.resource.PairResource;

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

  /** Why a link's issuer is told its link did not work. */
  public enum Refusal {
    EXPIRED,
    ORIGIN
  }

  /** Tells the player who made a link, when online, that a browser could not use it. */
  @FunctionalInterface
  public interface IssuerNotifier {
    void tell(UUID issuerId, String linkLabel, Refusal refusal, String origin);
  }

  private final Supplier<WebTokens> tokens;
  private final Supplier<AuditLog> audit;
  private final IssuerNotifier issuerNotifier;
  private final Map<String, Long> said = new ConcurrentHashMap<>();

  public PairingNotices(Supplier<WebTokens> tokens, Supplier<AuditLog> audit, IssuerNotifier issuerNotifier) {
    this.tokens = tokens;
    this.audit = audit;
    this.issuerNotifier = issuerNotifier;
  }

  @Override
  public void paired(WebToken token, String address) {
    audit.get().append("web:" + token.id(), "pair", "client=" + token.client().orElse("") + " address=" + address, "paired");
    WebLog.info("React Web paired " + token.id() + (token.label().isBlank() ? "" : " \"" + token.label() + "\"")
        + " as " + token.role() + token.client().map(client -> " from " + client).orElse(""));
  }

  @Override
  public void refused(String tokenId, WebError reason, String address) {
    audit.get().append("web:" + tokenId, "pair", "address=" + address, reason.code());
    if ("pairing_expired".equals(reason.code())) {
      if (due("pair " + tokenId, Duration.ofHours(1))) {
        WebLog.warn("React Web pairing " + tokenId + " failed: the link expired before it was opened; run /react web pair again");
        WebTokens store = tokens.get();
        if (store != null) {
          store.link(tokenId).ifPresent(link -> tellIssuer(link, Refusal.EXPIRED, ""));
        }
      }
      return;
    }
    if (due("pair from " + address, Duration.ofMinutes(1))) {
      WebLog.warn("React Web pairing from " + address + " failed: " + reason.code());
    }
  }

  /** A browser page from an origin outside {@code allowedOrigins}: logged once an hour, told to whoever has a link open. */
  public void originRefused(String origin) {
    if (origin == null) {
      return;
    }
    String shown = origin.length() > 128 ? origin.substring(0, 128) : origin;
    if (due("origin " + shown, Duration.ofHours(1))) {
      WebLog.warn("React Web refused origin " + shown + ": it is not in allowedOrigins in web.toml");
    }
    WebTokens store = tokens.get();
    if (store == null) {
      return;
    }
    for (WebTokens.OpenLink link : store.openLinks()) {
      if (due("origin notice " + shown + " " + link.tokenId(), Duration.ofHours(1))) {
        tellIssuer(link, Refusal.ORIGIN, shown);
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
  private void tellIssuer(WebTokens.OpenLink link, Refusal refusal, String origin) {
    Optional<UUID> issuer = link.grant().issuerId();
    if (issuer.isEmpty() || issuerNotifier == null) {
      return;
    }
    String who = link.grant().label().isBlank() ? link.tokenId() : link.grant().label();
    issuerNotifier.tell(issuer.get(), who, refusal, origin);
  }
}
