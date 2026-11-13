package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.WebError;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.WhoamiDto;
import art.arcane.react.api.web.pairing.WebToken;
import io.javalin.http.Context;

public class WhoamiResource {

    public void get(Context ctx) {
        WebToken token = ctx.attribute("token");
        if (token == null) {
            throw WebError.unauthorized();
        }
        ctx.json(new Envelope<>(describe(token)));
    }

    /** The token as {@code whoami} and the summary's {@code pairing} show it. */
    public static WhoamiDto describe(WebToken token) {
        WhoamiDto dto = new WhoamiDto();
        dto.tokenId = token.id();
        dto.label = token.label();
        dto.role = token.role();
        dto.scopes = token.scopes();
        dto.expiresAt = token.expires().toString();
        dto.lifetimeSeconds = token.lifetime().toSeconds();
        return dto;
    }
}
