package art.arcane.edict.api.request;

import art.arcane.edict.api.Confidence;
import art.arcane.edict.api.endpoint.EdictEndpoint;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

@Builder
@Data
public class EdictResponse {
    private EdictRequest request;
    private EdictEndpoint endpoint;
    private int matchScore;
    @Singular
    private List<EdictFieldResponse> fields;

    public int getScoreOffset() {
        return fields.stream().mapToInt(i -> ((Confidence.values().length - i.getConfidence().ordinal()))).sum()
            + Math.abs(request.getInputs().size() - endpoint.getFields().size()) + matchScore;
    }
}
