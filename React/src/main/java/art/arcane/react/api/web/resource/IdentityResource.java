package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.IdentityDto;
import io.javalin.http.Context;

import java.util.function.Supplier;

public class IdentityResource {
    private final Supplier<IdentityDto> identity;

    public IdentityResource(Supplier<IdentityDto> identity) {
        this.identity = identity;
    }

    public void get(Context ctx) {
        ctx.json(new Envelope<>(identity.get()));
    }
}
