package art.arcane.react.web;

import art.arcane.react.api.web.WebRole;
import art.arcane.react.api.web.pairing.WebToken;
import art.arcane.react.api.web.pairing.WebTokens;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Tokens for tests: paired the way a browser pairs, through a link redeemed in the data folder's store. */
public final class WebTestTokens {
  private WebTestTokens() {
  }

  /** Pairs a browser with {@code role}'s scopes into {@code dataFolder} and returns its bearer. */
  public static String issue(File dataFolder, String label, String role) {
    return issue(dataFolder, label, roleScopes(role));
  }

  public static String issue(File dataFolder, String label, List<String> scopes) {
    try {
      WebTokens tokens = WebTokens.load(dataFolder, Clock.systemUTC());
      WebTokens.Link link = tokens.pair(new WebTokens.Grant(label, scopes, Duration.ofDays(7), "test", Optional.empty()));
      return tokens.redeem(link.tokenId(), link.secret(), Optional.of(label)).bearer();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  /** The id part of a bearer. */
  public static String idOf(String bearer) {
    return bearer.substring(0, bearer.indexOf('.'));
  }

  /** A token as a request carries it after authentication, for resource tests that mock the context. */
  public static WebToken token(String id, String role) {
    Instant now = Instant.now();
    return new WebToken(id, "0".repeat(64), id, roleScopes(role), "test", now, now.plus(Duration.ofDays(7)), Duration.ofDays(7),
        Optional.empty(), Optional.empty());
  }

  private static List<String> roleScopes(String role) {
    WebRole webRole = WebRole.fromId(role);
    if (webRole == null) {
      throw new IllegalArgumentException("unknown role " + role);
    }
    return webRole.scopes();
  }
}
