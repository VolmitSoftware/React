package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.IncidentContributorDto;
import art.arcane.react.api.web.dto.IncidentDto;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import io.javalin.http.Context;

import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class IncidentResource {

    private final DoubleSupplier scoreSupplier;
    private final Supplier<String> stateSupplier;
    private final IntFunction<List<String>> timelineSupplier;
    private final Supplier<List<SamplerIncidentScore.Contribution>> contributorsSupplier;

    public IncidentResource(
            DoubleSupplier scoreSupplier,
            Supplier<String> stateSupplier,
            IntFunction<List<String>> timelineSupplier,
            Supplier<List<SamplerIncidentScore.Contribution>> contributorsSupplier) {
        this.scoreSupplier = scoreSupplier;
        this.stateSupplier = stateSupplier;
        this.timelineSupplier = timelineSupplier;
        this.contributorsSupplier = contributorsSupplier;
    }

    public void get(Context ctx) {
        String limitParam = ctx.queryParam("limit");
        int limit;
        if (limitParam == null) {
            limit = 20;
        } else {
            try {
                int parsed = Integer.parseInt(limitParam);
                limit = Math.max(1, Math.min(200, parsed));
            } catch (NumberFormatException e) {
                throw new io.javalin.http.BadRequestResponse();
            }
        }

        IncidentDto dto = new IncidentDto();
        dto.score = scoreSupplier.getAsDouble();
        dto.state = stateSupplier.get();
        dto.timeline = timelineSupplier.apply(limit).toArray(new String[0]);

        List<SamplerIncidentScore.Contribution> rawContribs = contributorsSupplier.get();
        IncidentContributorDto[] contribs = new IncidentContributorDto[rawContribs.size()];
        for (int i = 0; i < rawContribs.size(); i++) {
            SamplerIncidentScore.Contribution c = rawContribs.get(i);
            IncidentContributorDto cdto = new IncidentContributorDto();
            cdto.name = c.name();
            cdto.weight = c.weight();
            cdto.value = c.value();
            contribs[i] = cdto;
        }
        dto.contributors = contribs;

        ctx.json(new Envelope<>(dto));
    }
}
