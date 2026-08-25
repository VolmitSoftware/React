package art.arcane.react.api.web.dto;

import art.arcane.react.core.incident.IncidentRecord;

public class IncidentDto {
    public double score;
    public boolean scoreAvailable;
    public long sampledAtMs;
    public String state;
    public IncidentContributorDto[] contributors;
    public IncidentRecord[] incidents;
}
