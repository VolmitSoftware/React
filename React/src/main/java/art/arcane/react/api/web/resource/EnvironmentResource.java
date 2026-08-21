package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.EnvironmentDto;
import io.javalin.http.Context;

import java.util.function.Supplier;

public class EnvironmentResource {
    private final Supplier<EnvironmentDto> supplier;

    public EnvironmentResource(Supplier<EnvironmentDto> supplier) {
        this.supplier = supplier;
    }

    public void get(Context ctx) {
        ctx.json(new Envelope<>(supplier.get()));
    }
}
