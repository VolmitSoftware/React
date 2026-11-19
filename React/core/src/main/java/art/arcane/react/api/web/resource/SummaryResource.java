package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.WebAuth;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.IncidentSummaryDto;
import art.arcane.react.api.web.dto.SummaryDto;
import art.arcane.react.api.web.pairing.WebToken;
import art.arcane.react.core.incident.IncidentRecord;
import io.javalin.http.Context;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * {@code GET /api/v1/summary}: what a start page row needs from one server in one small call: its name, players, TPS,
 * tick time, incident state and open incidents, and when this pairing ends unless used.
 */
public class SummaryResource {
    private static final Set<String> OPEN_PHASES = Set.of("STARTED", "ACTION");
    private static final int INCIDENT_WINDOW = 200;

    private final Supplier<MetricsResource.LiveFrame> frames;
    private final Supplier<String> incidentState;
    private final IntFunction<List<IncidentRecord>> incidents;
    private final Supplier<String> serverName;
    private final Supplier<String> fingerprint;

    public SummaryResource(Supplier<MetricsResource.LiveFrame> frames, Supplier<String> incidentState,
                           IntFunction<List<IncidentRecord>> incidents, Supplier<String> serverName, Supplier<String> fingerprint) {
        this.frames = frames;
        this.incidentState = incidentState;
        this.incidents = incidents;
        this.serverName = serverName;
        this.fingerprint = fingerprint;
    }

    public void get(Context ctx) {
        WebAuth.requireScope(ctx, "read");
        WebToken token = ctx.attribute("token");
        MetricsResource.LiveFrame frame = frames.get();
        SummaryDto dto = new SummaryDto();
        dto.serverName = serverName.get();
        dto.fingerprint = fingerprint.get();
        dto.capturedAtMs = frame.capturedAtMs();
        for (MetricsResource.LiveSampleDto sample : frame.samplers()) {
            switch (sample.id()) {
                case "players" -> dto.players = (int) Math.round(sample.value());
                case "ticks-per-second" -> dto.tps = sample.value();
                case "tick-time" -> dto.tickMs = sample.value();
                default -> {
                }
            }
        }
        IncidentSummaryDto incident = new IncidentSummaryDto();
        incident.state = incidentState.get();
        incident.open = openIncidents(incidents.apply(INCIDENT_WINDOW));
        dto.incident = incident;
        dto.pairing = WhoamiResource.describe(token);
        ctx.json(new Envelope<>(dto));
    }

    /** Incidents whose latest record, newest first in {@code recent}, says they started and have not resolved. */
    static int openIncidents(List<IncidentRecord> recent) {
        Set<String> seen = new HashSet<>();
        int open = 0;
        for (IncidentRecord record : recent) {
            if (record.incidentId() == null || !seen.add(record.incidentId())) {
                continue;
            }
            if (OPEN_PHASES.contains(record.phase())) {
                open++;
            }
        }
        return open;
    }
}
