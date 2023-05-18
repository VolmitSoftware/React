package art.arcane.edict.api.request;

import art.arcane.edict.api.Confidence;
import art.arcane.edict.api.field.EdictField;
import art.arcane.edict.api.input.EdictInput;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EdictFieldResponse {
    private EdictField field;
    private EdictInput input;
    private Object value;
    private Confidence confidence;
    private int inputPosition;
    private int nameDrift;
}
