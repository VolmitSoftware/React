package art.arcane.react.api.web.dto;

/** {@code GET /api/v1/summary}: one small answer for a start page row. Unsampled values are null. */
public class SummaryDto {
    public String serverName;
    public String fingerprint;
    public long capturedAtMs;
    public Integer players;
    public Double tps;
    public Double tickMs;
    public IncidentSummaryDto incident;
    public WhoamiDto pairing;
}
