package art.arcane.edict.api.request;

import art.arcane.edict.Edict;
import art.arcane.edict.api.endpoint.EdictEndpoint;
import art.arcane.edict.api.field.EdictField;
import art.arcane.edict.api.input.EdictInput;
import com.google.gson.Gson;
import com.volmit.react.React;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Builder
@Data
@AllArgsConstructor
public class EdictRequest {
    private final List<EdictInput> inputs;
    private int matchOffset = 0;
    private EdictEndpoint pair;

    public EdictRequest(EdictRequest r) {
        this(new ArrayList<>(r.getInputs()));
        this.matchOffset = r.matchOffset;
        this.pair = r.pair;
    }

    public EdictRequest(List<EdictInput> edictInputs) {
        this.inputs = edictInputs;
    }

    public EdictRequest pathMatch(EdictEndpoint endpoint) {
        if(inputs.size() < endpoint.getPath().size()) {
            React.verbose(endpoint.getCommand() + " Against " + inputs.size() + " is too low");
            return null;
        }

        for(String i : endpoint.getPath()) {
            if(inputs.isEmpty()) {
                return null;
            }

            Optional<String> p = inputs.remove(0).into();

            if(p.isPresent()) {
                matchOffset += Edict.calculateLevenshteinDistance(p.get(), i);
            }

            else {
                return null;
            }
        }

        pair = endpoint;
        return this;
    }

    public static EdictRequest of(Edict edict, String args) {
        return of(edict, Edict.toArgs(args));
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

        var m = EdictRequest.builder()
            .inputs(edictInputs)
            .build();
        React.verbose("Request Built for " + String.join(" ", a) + " -> " + new Gson().toJson(m));

        return m;
    }
}
