package art.arcane.edict.api.request;

import art.arcane.edict.Edict;
import art.arcane.edict.api.endpoint.EdictEndpoint;
import art.arcane.edict.api.field.EdictField;
import art.arcane.edict.api.input.EdictInput;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Builder
@Data
@AllArgsConstructor
public class EdictRequest {
    private final List<EdictInput> inputs;
    private int matchOffset = 0;
    private EdictEndpoint pair;

    public EdictRequest(EdictRequest r) {
        this(r.getInputs());
    }

    public EdictRequest(List<EdictInput> edictInputs) {
        this.inputs = edictInputs;
    }

    public EdictRequest pathMatch(EdictEndpoint endpoint) {
        if(inputs.size() < endpoint.getFields().size()) {
            return null;
        }

        for(String i : endpoint.getPath()) {
            Optional<String> p = inputs.remove(0).into();

            if(p.isPresent()) {
                matchOffset += i.compareToIgnoreCase(p.get());
            }

            else {
                return null;
            }
        }

        pair = endpoint;
        return this;
    }

    public static EdictRequest of(Edict edict, String args) {
        return of(edict, Arrays.stream(args.split("\\Q \\E")).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
    }

    public static EdictRequest of(Edict edict, String[] args) {
        return of(edict, List.of(args));
    }

    public static EdictRequest of(Edict edict, List<String> a) {
        List<String> args = Edict.enhance(a);
        List<EdictInput> edictInputs = new ArrayList<>();
        int position = 0;
        int realPosition = 0;

        for(String i : args) {
            edictInputs.add(edict.parseInput(i, position, realPosition));

            if(!i.contains("=")) {
                position++;
            }

            realPosition++;
        }

        return EdictRequest.builder()
            .inputs(edictInputs)
            .build();
    }
}
