package art.arcane.react.web.pairing;

import art.arcane.react.api.web.WebError;
import art.arcane.react.api.web.pairing.WebToken;
import art.arcane.react.api.web.pairing.WebTokens;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

class WebTokensTest {
  private static final List<String> OPERATOR = List.of("read", "op:execute");

  @Test
  void aLinkRedeemsOnceForATokenThatAuthenticates(@TempDir File folder) throws Exception {
    MutableClock clock = new MutableClock();
    WebTokens tokens = WebTokens.load(folder, clock);
    WebTokens.Link link = tokens.pair(grant(Duration.ofDays(7)));

    Assertions.assertTrue(link.tokenId().matches("tok_[A-Za-z0-9_-]{16}"), link.tokenId());
    Assertions.assertTrue(link.secret().matches("[A-Za-z0-9_-]{43}"));
    Assertions.assertEquals(clock.instant().plus(WebTokens.LINK_LIFETIME), link.expires());

    WebTokens.Redeemed redeemed = tokens.redeem(link.tokenId(), link.secret(), Optional.of("Chrome on macOS"));
    Assertions.assertTrue(redeemed.bearer().startsWith(link.tokenId() + "."));
    Assertions.assertNotEquals(link.secret(), redeemed.bearer().substring(link.tokenId().length() + 1));

    WebToken token = tokens.authenticate(redeemed.bearer());
    Assertions.assertEquals(OPERATOR, token.scopes());
    Assertions.assertEquals("operator", token.role());
    Assertions.assertEquals(Optional.of("Chrome on macOS"), token.client());

    Assertions.assertEquals("pairing_used", code(() -> tokens.redeem(link.tokenId(), link.secret(), Optional.empty())));
  }

  @Test
  void aWrongSecretOrUnknownLinkIsUnknown(@TempDir File folder) throws Exception {
    WebTokens tokens = WebTokens.load(folder, new MutableClock());
    WebTokens.Link link = tokens.pair(grant(Duration.ofDays(7)));

    Assertions.assertEquals("pairing_unknown", code(() -> tokens.redeem(link.tokenId(), "A".repeat(43), Optional.empty())));
    Assertions.assertEquals("pairing_unknown", code(() -> tokens.redeem("tok_AAAAAAAAAAAAAAAA", link.secret(), Optional.empty())));
    Assertions.assertNotNull(tokens.redeem(link.tokenId(), link.secret(), Optional.empty()));
  }

  @Test
  void aLinkExpiresAfterTenMinutesAndIsForgottenAnHourLater(@TempDir File folder) throws Exception {
    MutableClock clock = new MutableClock();
    WebTokens tokens = WebTokens.load(folder, clock);
    WebTokens.Link link = tokens.pair(grant(Duration.ofDays(7)));

    clock.advance(Duration.ofMinutes(9).plusSeconds(59));
    Assertions.assertEquals(1, tokens.openLinks().size());
    clock.advance(Duration.ofSeconds(1));
    Assertions.assertTrue(tokens.openLinks().isEmpty());
    Assertions.assertEquals("pairing_expired", code(() -> tokens.redeem(link.tokenId(), link.secret(), Optional.empty())));
    Assertions.assertEquals("pairing_expired", code(() -> tokens.redeem(link.tokenId(), link.secret(), Optional.empty())));

    clock.advance(Duration.ofHours(1).plusSeconds(1));
    tokens.expire();
    Assertions.assertEquals("pairing_unknown", code(() -> tokens.redeem(link.tokenId(), link.secret(), Optional.empty())));
  }

  @Test
  void revokingAnUnopenedLinkCancelsIt(@TempDir File folder) throws Exception {
    WebTokens tokens = WebTokens.load(folder, new MutableClock());
    WebTokens.Link link = tokens.pair(grant(Duration.ofDays(7)));

    Assertions.assertEquals(List.of(link.tokenId()), tokens.revoke(link.tokenId()));
    Assertions.assertTrue(tokens.openLinks().isEmpty());
    Assertions.assertEquals("pairing_revoked", code(() -> tokens.redeem(link.tokenId(), link.secret(), Optional.empty())));
  }

  @Test
  void expirySlidesWithUseAndEndsWithoutIt(@TempDir File folder) throws Exception {
    MutableClock clock = new MutableClock();
    WebTokens tokens = WebTokens.load(folder, clock);
    WebTokens.Link link = tokens.pair(grant(Duration.ofMinutes(2)));
    String bearer = tokens.redeem(link.tokenId(), link.secret(), Optional.empty()).bearer();
    Instant paired = clock.instant();
    Assertions.assertEquals(paired.plus(Duration.ofMinutes(2)), tokens.find(link.tokenId()).orElseThrow().expires());

    clock.advance(Duration.ofSeconds(90));
    WebToken used = tokens.authenticate(bearer);
    Assertions.assertEquals(clock.instant().plus(Duration.ofMinutes(2)), used.expires());
    Assertions.assertEquals(Optional.of(clock.instant()), used.lastUsed());

    clock.advance(Duration.ofSeconds(90));
    tokens.touch(link.tokenId());
    Assertions.assertEquals(clock.instant().plus(Duration.ofMinutes(2)), tokens.find(link.tokenId()).orElseThrow().expires());

    clock.advance(Duration.ofMinutes(2));
    Assertions.assertEquals("token_expired", code(() -> tokens.authenticate(bearer)));
    Assertions.assertTrue(tokens.find(link.tokenId()).isEmpty());
  }

  @Test
  void housekeepingEndsUnusedTokens(@TempDir File folder) throws Exception {
    MutableClock clock = new MutableClock();
    WebTokens tokens = WebTokens.load(folder, clock);
    WebTokens.Link link = tokens.pair(grant(Duration.ofHours(1)));
    String bearer = tokens.redeem(link.tokenId(), link.secret(), Optional.empty()).bearer();

    clock.advance(Duration.ofHours(1));
    Assertions.assertEquals(List.of(link.tokenId()), tokens.expire());
    Assertions.assertEquals("token_expired", code(() -> tokens.authenticate(bearer)));
  }

  @Test
  void revokedTokensAreToldApart(@TempDir File folder) throws Exception {
    WebTokens tokens = WebTokens.load(folder, new MutableClock());
    WebTokens.Link link = tokens.pair(grant(Duration.ofDays(7)));
    String bearer = tokens.redeem(link.tokenId(), link.secret(), Optional.empty()).bearer();

    Assertions.assertEquals(List.of(link.tokenId()), tokens.revoke(link.tokenId()));
    Assertions.assertEquals("token_revoked", code(() -> tokens.authenticate(bearer)));
    Assertions.assertEquals("unauthorized", code(() -> tokens.authenticate("tok_AAAAAAAAAAAAAAAA." + "A".repeat(43))));
    Assertions.assertEquals("unauthorized", code(() -> tokens.authenticate("garbage")));
    Assertions.assertEquals(List.of(), tokens.revoke(link.tokenId()));
  }

  @Test
  void revokeAllEndsTokensAndLinks(@TempDir File folder) throws Exception {
    WebTokens tokens = WebTokens.load(folder, new MutableClock());
    WebTokens.Link first = tokens.pair(grant(Duration.ofDays(7)));
    tokens.redeem(first.tokenId(), first.secret(), Optional.empty());
    WebTokens.Link open = tokens.pair(grant(Duration.ofDays(7)));

    Assertions.assertEquals(List.of(first.tokenId(), open.tokenId()), tokens.revoke("all"));
    Assertions.assertTrue(tokens.list().isEmpty());
    Assertions.assertFalse(tokens.wanted());
  }

  @Test
  void tokensAndEndingsSurviveAReload(@TempDir File folder) throws Exception {
    MutableClock clock = new MutableClock();
    WebTokens tokens = WebTokens.load(folder, clock);
    WebTokens.Link kept = tokens.pair(new WebTokens.Grant("laptop", OPERATOR, Duration.ofDays(7), "console", Optional.empty()));
    String keptBearer = tokens.redeem(kept.tokenId(), kept.secret(), Optional.of("Firefox")).bearer();
    WebTokens.Link gone = tokens.pair(grant(Duration.ofDays(7)));
    String goneBearer = tokens.redeem(gone.tokenId(), gone.secret(), Optional.empty()).bearer();
    tokens.revoke(gone.tokenId());
    WebTokens.Link open = tokens.pair(grant(Duration.ofDays(7)));

    String stored = Files.readString(new File(folder, WebTokens.FILE).toPath());
    Assertions.assertFalse(stored.contains(keptBearer.substring(keptBearer.indexOf('.') + 1)), "the bearer secret is never stored");

    WebTokens reloaded = WebTokens.load(folder, clock);
    WebToken token = reloaded.authenticate(keptBearer);
    Assertions.assertEquals("laptop", token.label());
    Assertions.assertEquals(Optional.of("Firefox"), token.client());
    Assertions.assertEquals("console", token.issuer());
    Assertions.assertEquals("token_revoked", code(() -> reloaded.authenticate(goneBearer)));
    Assertions.assertEquals("pairing_unknown", code(() -> reloaded.redeem(open.tokenId(), open.secret(), Optional.empty())),
        "links live in memory only");
  }

  @Test
  void aSlidExpiryIsWrittenOnceItMovedFarEnough(@TempDir File folder) throws Exception {
    MutableClock clock = new MutableClock();
    WebTokens tokens = WebTokens.load(folder, clock);
    WebTokens.Link link = tokens.pair(grant(Duration.ofMinutes(10)));
    String bearer = tokens.redeem(link.tokenId(), link.secret(), Optional.empty()).bearer();

    clock.advance(Duration.ofMinutes(5));
    tokens.authenticate(bearer);
    Instant slid = clock.instant().plus(Duration.ofMinutes(10));

    WebTokens reloaded = WebTokens.load(folder, clock);
    Assertions.assertEquals(slid, reloaded.find(link.tokenId()).orElseThrow().expires());
  }

  @Test
  void theLinkAndTokenLimitsHold(@TempDir File folder) throws Exception {
    WebTokens tokens = WebTokens.load(folder, new MutableClock());
    for (int index = 0; index < WebTokens.MAXIMUM_LINKS; index++) {
      tokens.pair(grant(Duration.ofDays(7)));
    }
    Assertions.assertThrows(IllegalStateException.class, () -> tokens.pair(grant(Duration.ofDays(7))));
  }

  @Test
  void aGrantRefusesBadInput() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new WebTokens.Grant("x".repeat(65), OPERATOR, Duration.ofDays(1), "console", Optional.empty()));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new WebTokens.Grant("", List.of(), Duration.ofDays(1), "console", Optional.empty()));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new WebTokens.Grant("", OPERATOR, Duration.ofDays(366), "console", Optional.empty()));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new WebTokens.Grant("", OPERATOR, Duration.ofSeconds(30), "console", Optional.empty()));
  }

  private static WebTokens.Grant grant(Duration lifetime) {
    return new WebTokens.Grant("", OPERATOR, lifetime, "console", Optional.empty());
  }

  private static String code(ThrowingRunnable action) {
    WebError error = Assertions.assertThrows(WebError.class, action::run);
    return error.code();
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-11-13T10:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
