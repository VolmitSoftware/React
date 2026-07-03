package art.arcane.react.api.web.resource;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.MetricsSerializer;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.SamplerDto;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.util.project.registry.Registry;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MetricsResource {
    private final SampleController samplers;
    private final MetricsSerializer serializer;

    public MetricsResource(SampleController samplers, MetricsSerializer serializer) {
        this.samplers = samplers;
        this.serializer = serializer;
    }

    public SamplerDto[] snapshotData() {
        Registry<Sampler> reg = samplers.getSamplers();
        if (reg == null) {
            return new SamplerDto[0];
        }
        Collection<Sampler> all = reg.all();
        List<SamplerDto> dtos = new ArrayList<>();
        for (Sampler s : all) {
            dtos.add(serializer.toDto(s));
        }
        return dtos.toArray(new SamplerDto[0]);
    }

    public void snapshot(Context ctx) {
        ctx.json(new SnapshotResponse(snapshotData()));
    }

    public void history(Context ctx) {
        String id = ctx.pathParam("id");
        Sampler s = samplers.getSampler(id);
        if (s == null) {
            throw new NotFoundResponse();
        }
        ctx.json(new Envelope<>(serializer.toDto(s)));
    }

    public record SnapshotResponse(SamplerDto[] data) {}
}
