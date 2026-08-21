package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.WhoamiDto;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;

public class WhoamiResource {

    public void get(Context ctx) {
        PairingToken token = ctx.attribute("token");
        if (token == null) {
            throw new UnauthorizedResponse("No authenticated token");
        }
        WhoamiDto dto = new WhoamiDto();
        dto.role = token.role();
        dto.scopes = token.scopes();
        ctx.json(new Envelope<>(dto));
    }
}
