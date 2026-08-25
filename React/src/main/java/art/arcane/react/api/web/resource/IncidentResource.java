package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.IncidentContributorDto;
import art.arcane.react.api.web.dto.IncidentDto;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.core.incident.IncidentEvidence;
import art.arcane.react.core.incident.IncidentRecord;
import io.javalin.http.Context;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class IncidentResource {

    private final Supplier<SamplerIncidentScore.IncidentScoreSnapshot> snapshotSupplier;
    private final Supplier<String> stateSupplier;
    private final IntFunction<List<IncidentRecord>> recordsSupplier;

    public IncidentResource(
            Supplier<SamplerIncidentScore.IncidentScoreSnapshot> snapshotSupplier,
            Supplier<String> stateSupplier,
            IntFunction<List<IncidentRecord>> recordsSupplier) {
        this.snapshotSupplier = snapshotSupplier;
        this.stateSupplier = stateSupplier;
        this.recordsSupplier = recordsSupplier;
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
        SamplerIncidentScore.IncidentScoreSnapshot snapshot = snapshotSupplier.get();
        if (snapshot == null) {
            snapshot = SamplerIncidentScore.IncidentScoreSnapshot.empty();
        }
        dto.score = snapshot.score();
        dto.scoreAvailable = snapshot.available();
        dto.sampledAtMs = snapshot.sampledAtMs();
        dto.state = stateSupplier.get();
        dto.incidents = recordsSupplier.apply(limit).toArray(new IncidentRecord[0]);

        List<IncidentEvidence> rawContribs = snapshot.evidence();
        IncidentContributorDto[] contribs = new IncidentContributorDto[rawContribs.size()];
        for (int i = 0; i < rawContribs.size(); i++) {
            IncidentEvidence c = rawContribs.get(i);
            IncidentContributorDto cdto = new IncidentContributorDto();
            cdto.id = c.metricId();
            cdto.label = c.label();
            cdto.available = c.available();
            cdto.weight = c.weight();
            cdto.value = c.value();
            cdto.display = c.display();
            cdto.pressure = c.pressure();
            cdto.scorePoints = c.scorePoints();
            cdto.minimum = c.minimum();
            cdto.maximum = c.maximum();
            contribs[i] = cdto;
        }
        dto.contributors = contribs;

        ctx.json(new Envelope<>(dto));
    }
}
