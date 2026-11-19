package art.arcane.react.api.web.pairing;

import art.arcane.react.api.web.WebError;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * React Web tokens in {@code web/tokens.json}, ported from the Animus editor's token store. Only a SHA-256 of each bearer
 * is stored. Expiry slides: each use moves it to one lifetime after that use. Pairing links live in memory only and work
 * once for ten minutes; a used, revoked or expired link is remembered for an hour so a late browser learns which.
 */
public final class WebTokens {
  public static final String FILE = "web/tokens.json";
  public static final Duration LINK_LIFETIME = Duration.ofMinutes(10);
  public static final Duration LINK_MEMORY = Duration.ofHours(1);
  public static final Duration ENDED_MEMORY = Duration.ofDays(1);
  public static final Duration MINIMUM_LIFETIME = Duration.ofMinutes(1), MAXIMUM_LIFETIME = Duration.ofDays(365);
  public static final int MAXIMUM_TOKENS = 256, MAXIMUM_LINKS = 64;
  private static final Duration SLIDE_WRITE = Duration.ofHours(1);
  private static final String BEARER = "tok_[A-Za-z0-9_-]{16}\\.[A-Za-z0-9_-]{43}";
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

  /** What {@code /react web pair} asked for. {@code issuerId} is the player who made the link, empty for the console. */
  public record Grant(String label, List<String> scopes, Duration lifetime, String issuer, Optional<UUID> issuerId) {
    public Grant {
      Objects.requireNonNull(label, "label");
      if (label.length() > 64) {
        throw new IllegalArgumentException("label: at most 64 characters");
      }
      if (scopes.isEmpty()) {
        throw new IllegalArgumentException("scopes: at least one scope");
      }
      if (lifetime.compareTo(MINIMUM_LIFETIME) < 0 || lifetime.compareTo(MAXIMUM_LIFETIME) > 0) {
        throw new IllegalArgumentException("expires: 1 minute to 365 days");
      }
      scopes = List.copyOf(scopes);
    }
  }

  /** A link waiting for its browser, with the secret that redeems it. */
  public record Link(String tokenId, String secret, Instant expires, Grant grant) {
  }

  /** A link not opened yet, without its secret. */
  public record OpenLink(String tokenId, Instant expires, Grant grant) {
  }

  /** A redeemed link: the bearer, shown once, and its token. */
  public record Redeemed(String bearer, WebToken token) {
  }

  private enum LinkState { OPEN, USED, REVOKED }

  private record Pending(String secretHash, Instant expires, Grant grant, LinkState state, Instant ended) {
    Pending ended(LinkState next, Instant at) {
      return new Pending(secretHash, expires, grant, next, at);
    }

    Instant forgetAt() {
      return (state == LinkState.OPEN ? expires : ended).plus(LINK_MEMORY);
    }
  }

  private record Ended(boolean revoked, Instant forgetAt) {
  }

  private final Path file;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, WebToken> tokens = new LinkedHashMap<>();
  private final Map<String, Instant> written = new LinkedHashMap<>();
  private final Map<String, Ended> ended = new LinkedHashMap<>();
  private final Map<String, Pending> links = new LinkedHashMap<>();

  private WebTokens(Path file, Clock clock) {
    this.file = file;
    this.clock = clock;
  }

  /** Reads {@code web/tokens.json} under {@code dataFolder}, or starts empty when it does not exist yet. */
  public static WebTokens load(File dataFolder, Clock clock) throws IOException {
    WebTokens store = new WebTokens(new File(dataFolder, FILE).toPath(), Objects.requireNonNull(clock));
    store.read();
    return store;
  }

  /** Starts a link; the secret is good once for ten minutes. */
  public synchronized Link pair(Grant grant) {
    prune();
    if (openCount() >= MAXIMUM_LINKS) {
      throw new IllegalStateException("Too many pairing links are waiting; let some expire first");
    }
    if (tokens.size() >= MAXIMUM_TOKENS) {
      throw new IllegalStateException("Token limit of " + MAXIMUM_TOKENS + " reached; revoke unused tokens first");
    }
    String id = "tok_" + randomText(12);
    String secret = randomText(32);
    Instant expires = now().plus(LINK_LIFETIME);
    links.put(id, new Pending(sha256(secret), expires, grant, LinkState.OPEN, expires));
    return new Link(id, secret, expires, grant);
  }

  /** Exchanges a link's secret for a token. */
  public synchronized Redeemed redeem(String tokenId, String secret, Optional<String> client) throws IOException {
    Pending pending = links.get(tokenId);
    if (pending == null || secret == null || !hashEquals(pending.secretHash(), sha256(secret))) {
      throw WebError.pairingUnknown();
    }
    Instant now = now();
    if (pending.state() == LinkState.USED) {
      throw WebError.pairingUsed();
    }
    if (pending.state() == LinkState.REVOKED) {
      throw WebError.pairingRevoked();
    }
    if (!now.isBefore(pending.expires())) {
      throw WebError.pairingExpired();
    }
    if (tokens.size() >= MAXIMUM_TOKENS) {
      throw WebError.unavailable("Token limit of " + MAXIMUM_TOKENS + " reached; revoke unused tokens first.");
    }
    Grant grant = pending.grant();
    String bearer = tokenId + "." + randomText(32);
    WebToken token = new WebToken(tokenId, sha256(bearer), grant.label(), grant.scopes(), grant.issuer(), now,
        now.plus(grant.lifetime()), grant.lifetime(), Optional.empty(),
        client.map(String::strip).filter(name -> !name.isEmpty()).map(name -> name.length() > 128 ? name.substring(0, 128) : name));
    tokens.put(tokenId, token);
    links.put(tokenId, pending.ended(LinkState.USED, now));
    try {
      save();
    } catch (IOException failure) {
      tokens.remove(tokenId);
      links.put(tokenId, pending);
      throw failure;
    }
    return new Redeemed(bearer, token);
  }

  /** The token a bearer names, its expiry slid forward, or why it is refused. Constant-time hash comparison. */
  public synchronized WebToken authenticate(String bearer) {
    if (bearer == null || !bearer.matches(BEARER)) {
      throw WebError.unauthorized();
    }
    String id = bearer.substring(0, bearer.indexOf('.'));
    Ended end = ended.get(id);
    if (end != null) {
      throw end.revoked() ? WebError.tokenRevoked() : WebError.tokenExpired();
    }
    WebToken token = tokens.get(id);
    if (token == null || !hashEquals(token.hash(), sha256(bearer))) {
      throw WebError.unauthorized();
    }
    Instant now = now();
    if (!now.isBefore(token.expires())) {
      end(id, false);
      saveQuietly();
      throw WebError.tokenExpired();
    }
    return slide(token, now);
  }

  /** Counts a use without a request, such as an open socket, so a pairing in use never ends. */
  public synchronized void touch(String tokenId) {
    WebToken token = tokens.get(tokenId);
    Instant now = now();
    if (token != null && now.isBefore(token.expires())) {
      slide(token, now);
    }
  }

  /**
   * Revokes one token, or every token for {@code all}. A link not opened yet is cancelled the same way. Returns the ended
   * ids in the order they were made.
   */
  public synchronized List<String> revoke(String idOrAll) throws IOException {
    boolean all = "all".equals(idOrAll);
    Instant now = now();
    List<String> ids = new ArrayList<>();
    boolean stored = false;
    for (String id : new ArrayList<>(tokens.keySet())) {
      if (all || id.equals(idOrAll)) {
        end(id, true);
        ids.add(id);
        stored = true;
      }
    }
    for (Map.Entry<String, Pending> link : links.entrySet()) {
      Pending pending = link.getValue();
      if ((all || link.getKey().equals(idOrAll)) && pending.state() == LinkState.OPEN && now.isBefore(pending.expires())) {
        link.setValue(pending.ended(LinkState.REVOKED, now));
        ids.add(link.getKey());
      }
    }
    if (stored) {
      save();
    }
    return List.copyOf(ids);
  }

  /** Ends tokens past their expiry and forgets old links and endings. Returns the ids that expired now. */
  public synchronized List<String> expire() throws IOException {
    Instant now = now();
    List<String> expired = new ArrayList<>();
    for (WebToken token : tokens.values()) {
      if (!now.isBefore(token.expires())) {
        expired.add(token.id());
      }
    }
    boolean changed = !expired.isEmpty();
    for (String id : expired) {
      end(id, false);
    }
    Iterator<Ended> old = ended.values().iterator();
    while (old.hasNext()) {
      if (old.next().forgetAt().isBefore(now)) {
        old.remove();
        changed = true;
      }
    }
    prune();
    if (changed) {
      save();
    }
    return expired;
  }

  public synchronized List<WebToken> list() {
    return List.copyOf(tokens.values());
  }

  public synchronized Optional<WebToken> find(String id) {
    return Optional.ofNullable(tokens.get(id));
  }

  /** Links not opened yet and not expired. */
  public synchronized List<OpenLink> openLinks() {
    Instant now = now();
    List<OpenLink> open = new ArrayList<>();
    links.forEach((id, pending) -> {
      if (pending.state() == LinkState.OPEN && now.isBefore(pending.expires())) {
        open.add(new OpenLink(id, pending.expires(), pending.grant()));
      }
    });
    return open;
  }

  /** A link with this id that was never redeemed, expired or not, while it is remembered. */
  public synchronized Optional<OpenLink> link(String tokenId) {
    Pending pending = links.get(tokenId);
    return pending == null || pending.state() != LinkState.OPEN
        ? Optional.empty()
        : Optional.of(new OpenLink(tokenId, pending.expires(), pending.grant()));
  }

  /** True while someone paired or is pairing: the relay connects only then. */
  public synchronized boolean wanted() {
    return !tokens.isEmpty() || openCount() > 0;
  }

  private int openCount() {
    return openLinks().size();
  }

  private WebToken slide(WebToken token, Instant now) {
    Instant slid = now.plus(token.lifetime());
    WebToken used = token.used(now, slid.isAfter(token.expires()) ? slid : token.expires());
    tokens.put(token.id(), used);
    Duration step = token.lifetime().dividedBy(10);
    Instant stored = written.getOrDefault(token.id(), token.created());
    if (used.expires().isAfter(stored.plus(step.compareTo(SLIDE_WRITE) < 0 ? step : SLIDE_WRITE))) {
      saveQuietly();
    }
    return used;
  }

  private void end(String id, boolean revoked) {
    WebToken token = tokens.remove(id);
    written.remove(id);
    Instant until = token == null || token.expires().isBefore(now()) ? now() : token.expires();
    ended.put(id, new Ended(revoked, until.plus(ENDED_MEMORY)));
  }

  private void prune() {
    Instant now = now();
    links.values().removeIf(pending -> now.isAfter(pending.forgetAt()));
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.SECONDS);
  }

  private String randomText(int bytes) {
    byte[] value = new byte[bytes];
    random.nextBytes(value);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  static String sha256(String text) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException(missing);
    }
  }

  private static boolean hashEquals(String expected, String actual) {
    return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
  }

  private void saveQuietly() {
    try {
      save();
    } catch (IOException ignored) {
      // The new expiry holds in memory and is written with the next change.
    }
  }

  private void read() throws IOException {
    if (!Files.exists(file)) {
      return;
    }
    try {
      JsonObject saved = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
      for (JsonElement element : saved.getAsJsonArray("tokens")) {
        JsonObject entry = element.getAsJsonObject();
        List<String> scopes = new ArrayList<>();
        entry.getAsJsonArray("scopes").forEach(scope -> scopes.add(scope.getAsString()));
        WebToken token = new WebToken(text(entry, "id"), text(entry, "hash"), text(entry, "label"), scopes, text(entry, "issuer"),
            Instant.parse(text(entry, "created")), Instant.parse(text(entry, "expires")),
            Duration.ofSeconds(entry.get("lifetimeSeconds").getAsLong()), optional(entry, "lastUsed").map(Instant::parse),
            optional(entry, "client"));
        tokens.put(token.id(), token);
        written.put(token.id(), token.expires());
      }
      if (saved.has("ended")) {
        for (JsonElement element : saved.getAsJsonArray("ended")) {
          JsonObject entry = element.getAsJsonObject();
          ended.put(text(entry, "id"), new Ended("revoked".equals(text(entry, "reason")), Instant.parse(text(entry, "forgetAt"))));
        }
      }
    } catch (RuntimeException invalid) {
      throw new IOException(FILE + " is unreadable; move it away to unpair every browser: " + invalid.getMessage(), invalid);
    }
  }

  private void save() throws IOException {
    JsonObject saved = new JsonObject();
    saved.addProperty("schemaVersion", 1);
    JsonArray list = new JsonArray();
    for (WebToken token : tokens.values()) {
      JsonObject entry = new JsonObject();
      entry.addProperty("id", token.id());
      entry.addProperty("hash", token.hash());
      entry.addProperty("label", token.label());
      JsonArray scopes = new JsonArray();
      token.scopes().forEach(scopes::add);
      entry.add("scopes", scopes);
      entry.addProperty("issuer", token.issuer());
      entry.addProperty("created", token.created().toString());
      entry.addProperty("expires", token.expires().toString());
      entry.addProperty("lifetimeSeconds", token.lifetime().toSeconds());
      token.lastUsed().ifPresent(used -> entry.addProperty("lastUsed", used.toString()));
      token.client().ifPresent(client -> entry.addProperty("client", client));
      list.add(entry);
    }
    saved.add("tokens", list);
    JsonArray gone = new JsonArray();
    ended.forEach((id, end) -> {
      JsonObject entry = new JsonObject();
      entry.addProperty("id", id);
      entry.addProperty("reason", end.revoked() ? "revoked" : "expired");
      entry.addProperty("forgetAt", end.forgetAt().toString());
      gone.add(entry);
    });
    saved.add("ended", gone);
    write(GSON.toJson(saved));
    tokens.values().forEach(token -> written.put(token.id(), token.expires()));
  }

  private void write(String content) throws IOException {
    Files.createDirectories(file.getParent());
    Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
    Files.writeString(temporary, content, StandardCharsets.UTF_8);
    try {
      Files.setPosixFilePermissions(temporary, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException ignored) {
      // Not a POSIX file system.
    }
    try {
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException unsupported) {
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static String text(JsonObject entry, String key) {
    JsonElement value = entry.get(key);
    if (value == null || value.isJsonNull()) {
      throw new IllegalArgumentException("missing " + key);
    }
    return value.getAsString();
  }

  private static Optional<String> optional(JsonObject entry, String key) {
    JsonElement value = entry.get(key);
    return value == null || value.isJsonNull() ? Optional.empty() : Optional.of(value.getAsString());
  }
}
