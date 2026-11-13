package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.WebError;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.PairResponseDto;
import art.arcane.react.api.web.pairing.WebToken;
import art.arcane.react.api.web.pairing.WebTokens;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** {@code POST /api/v1/pair}: exchanges a pairing link's one-time secret for a token. Open, like Animus's /v1/pair. */
public class PairResource {
    public static final int MAXIMUM_BODY_BYTES = 4096;
    private static final Set<String> FIELDS = Set.of("tokenId", "secret", "client");

    /** What a pairing attempt tells the server log and the player who made the link. */
    public interface Events {
        void paired(WebToken token, String address);

        void refused(String tokenId, WebError reason, String address);
    }

    private final WebTokens tokens;
    private final Supplier<String> fingerprint;
    private final Supplier<String> serverName;
    private final Events events;

    public PairResource(WebTokens tokens, Supplier<String> fingerprint, Supplier<String> serverName, Events events) {
        this.tokens = tokens;
        this.fingerprint = fingerprint;
        this.serverName = serverName;
        this.events = events;
    }

    public void pair(Context ctx) throws IOException {
        if (ctx.contentLength() > MAXIMUM_BODY_BYTES) {
            throw new WebError(413, "too_large", "A pairing request is at most " + MAXIMUM_BODY_BYTES + " bytes.");
        }
        byte[] raw = ctx.bodyAsBytes();
        if (raw.length > MAXIMUM_BODY_BYTES) {
            throw new WebError(413, "too_large", "A pairing request is at most " + MAXIMUM_BODY_BYTES + " bytes.");
        }
        JsonObject body;
        try {
            body = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException invalid) {
            throw WebError.badRequest("Expected a JSON object with tokenId and secret.");
        }
        for (String key : body.keySet()) {
            if (!FIELDS.contains(key)) {
                throw WebError.badRequest("Unknown field " + key + ".");
            }
        }
        String tokenId = text(body, "tokenId").orElseThrow(() -> WebError.badRequest("tokenId is required."));
        String secret = text(body, "secret").orElseThrow(() -> WebError.badRequest("secret is required."));
        Optional<String> client = text(body, "client");
        String address = ctx.ip();
        WebTokens.Redeemed redeemed;
        try {
            redeemed = tokens.redeem(tokenId, secret, client);
        } catch (WebError refused) {
            events.refused(tokenId, refused, address);
            throw refused;
        }
        WebToken token = redeemed.token();
        events.paired(token, address);
        PairResponseDto dto = new PairResponseDto();
        dto.token = redeemed.bearer();
        dto.tokenId = token.id();
        dto.label = token.label();
        dto.role = token.role();
        dto.scopes = token.scopes();
        dto.expiresAt = token.expires().toString();
        dto.lifetimeSeconds = token.lifetime().toSeconds();
        dto.fingerprint = fingerprint.get();
        dto.serverName = serverName.get();
        ctx.json(new Envelope<>(dto));
    }

    private static Optional<String> text(JsonObject body, String key) {
        JsonElement value = body.get(key);
        if (value == null || value.isJsonNull()) {
            return Optional.empty();
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().length() > 256) {
            throw WebError.badRequest(key + " must be a string of at most 256 characters.");
        }
        return Optional.of(value.getAsString());
    }
}
