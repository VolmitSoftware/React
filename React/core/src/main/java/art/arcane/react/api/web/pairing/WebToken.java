package art.arcane.react.api.web.pairing;

import art.arcane.react.api.web.WebRole;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A paired browser. {@code hash} is the SHA-256 of the bearer; the bearer itself is never stored. {@code expires} is one
 * {@code lifetime} after the last use. {@code issuer} is the player name or {@code console} that made the link.
 */
public record WebToken(String id, String hash, String label, List<String> scopes, String issuer, Instant created,
                       Instant expires, Duration lifetime, Optional<Instant> lastUsed, Optional<String> client) {
  public WebToken {
    scopes = List.copyOf(scopes);
  }

  public boolean hasScope(String scope) {
    return scopes.contains(scope);
  }

  /** {@code viewer}, {@code operator}, {@code admin} or {@code custom}. */
  public String role() {
    return WebRole.roleOf(scopes);
  }

  WebToken used(Instant now, Instant until) {
    return new WebToken(id, hash, label, scopes, issuer, created, until, lifetime, Optional.of(now), client);
  }
}
