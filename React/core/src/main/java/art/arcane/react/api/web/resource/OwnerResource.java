package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.OwnerDto;
import io.javalin.http.Context;

import java.util.List;
import java.util.function.Supplier;

public class OwnerResource {
    private final Supplier<List<OwnerDto>> owners;

    public OwnerResource(Supplier<List<OwnerDto>> owners) {
        this.owners = owners;
    }

    public void list(Context ctx) {
        ctx.json(new Envelope<>(owners.get()));
    }
}
