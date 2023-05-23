package art.arcane.edict.api.request;

import art.arcane.edict.Edict;
import art.arcane.edict.api.context.EdictContext;
import art.arcane.edict.api.context.EdictContextual;
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

/**
 * The `EdictRequest` class represents a request processed by the Edict system.
 * An EdictRequest is composed of a list of EdictInputs, and optionally a match offset and a paired EdictEndpoint.
 *
 * @see EdictInput
 * @see EdictEndpoint
 */
@Builder
@Data
@AllArgsConstructor
public class EdictRequest {
    /**
     * A list of EdictInput objects that represent the inputs to the request.
     */
    private final List<EdictInput> inputs;

    /**
     * An offset used in the matching process of an EdictEndpoint.
     */
    private int matchOffset = 0;

    /**
     * The EdictEndpoint that is paired with this request.
     */
    private EdictEndpoint pair;

    /**
     * Constructs a new EdictRequest from another EdictRequest.
     * This is a copy constructor.
     *
     * @param r the EdictRequest to copy.
     */
    public EdictRequest(EdictRequest r) {
        this(new ArrayList<>(r.getInputs()));
        this.matchOffset = r.matchOffset;
        this.pair = r.pair;
    }

    /**
     * Constructs a new EdictRequest from a list of EdictInputs.
     *
     * @param edictInputs the list of EdictInputs.
     */
    public EdictRequest(List<EdictInput> edictInputs) {
        this.inputs = edictInputs;
    }

    /**
     * Tries to match the path of the provided EdictEndpoint to the inputs of this EdictRequest.
     *
     * @param endpoint the EdictEndpoint to match.
     * @return this EdictRequest if a match is found, or null otherwise.
     */
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
               try {
                   matchOffset += Edict.calculateLevenshteinDistance(p.get(), i);
               }

               catch(Throwable e) {
                   return null;
               }
            }

            else {
                return null;
            }
        }

        pair = endpoint;
        return this;
    }

    /**
     * Constructs an EdictRequest from a given Edict and a string of arguments.
     *
     * @param edict the Edict to use.
     * @param args the string of arguments.
     * @return the constructed EdictRequest.
     */
    public static EdictRequest of(Edict edict, String args) {
        return of(edict, Edict.toArgs(args));
    }

    /**
     * Constructs an EdictRequest from a given Edict and an array of strings.
     *
     * @param edict the Edict to use.
     * @param args the array of arguments.
     * @return the constructed EdictRequest.
     */
    public static EdictRequest of(Edict edict, String[] args) {
        return of(edict, List.of(args));
    }

    /**
     * Constructs an EdictRequest from a given Edict and a list of strings.
     *
     * @param edict the Edict to use.
     * @param a the list of arguments.
     * @return the constructed EdictRequest.
     */
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
        React.verbose("Request Built for " + String.join(" ", a));

        return m;
    }
}
