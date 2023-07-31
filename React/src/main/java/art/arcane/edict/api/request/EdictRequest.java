/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.edict.api.request;

import art.arcane.edict.Edict;
import art.arcane.edict.api.endpoint.EdictEndpoint;
import art.arcane.edict.api.input.EdictInput;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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
     * The raw arguments of the request.
     */
    private List<String> rawArgs;

    /**
     * Constructs a new EdictRequest from another EdictRequest.
     * This is a copy constructor.
     *
     * @param r the EdictRequest to copy.
     */
    public EdictRequest(EdictRequest r) {
        this(new ArrayList<>(r.getInputs()));
        this.matchOffset = r.matchOffset;
        this.rawArgs = new ArrayList<>(r.rawArgs);
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
     * Constructs an EdictRequest from a given Edict and a string of arguments.
     *
     * @param edict the Edict to use.
     * @param args  the string of arguments.
     * @return the constructed EdictRequest.
     */
    public static EdictRequest of(Edict edict, String args) {
        return of(edict, Edict.toArgs(args));
    }

    /**
     * Constructs an EdictRequest from a given Edict and an array of strings.
     *
     * @param edict the Edict to use.
     * @param args  the array of arguments.
     * @return the constructed EdictRequest.
     */
    public static EdictRequest of(Edict edict, String[] args) {
        return of(edict, List.of(args));
    }

    /**
     * Constructs an EdictRequest from a given Edict and a list of strings.
     *
     * @param edict the Edict to use.
     * @param a     the list of arguments.
     * @return the constructed EdictRequest.
     */
    public static EdictRequest of(Edict edict, List<String> a) {
        List<String> args = Edict.enhance(a);
        List<EdictInput> edictInputs = new ArrayList<>();
        int position = 0;
        int realPosition = 0;

        for (String i : args) {
            edictInputs.add(edict.parseInput(i, position, realPosition));

            if (!i.contains("=")) {
                position++;
            }

            realPosition++;
        }

        var m = EdictRequest.builder()
                .inputs(edictInputs)
                .rawArgs(a)
                .build();

        return m;
    }

    public boolean shouldTrim() {
        return Edict.enhance(rawArgs).size() != rawArgs.size();
    }

    public List<String> getHeaderPath() {
        List<String> path = new ArrayList<>();

        for (EdictInput i : getInputs()) {
            try {
                path.add(i.into(String.class).orElseThrow());
            } catch (Throwable e) {
                break;
            }
        }

        return path;
    }

    public EdictRequest pathMatch(EdictEndpoint endpoint) {
        return pathMatch(endpoint, false);
    }

    /**
     * Tries to match the path of the provided EdictEndpoint to the inputs of this EdictRequest.
     *
     * @param endpoint the EdictEndpoint to match.
     * @return this EdictRequest if a match is found, or null otherwise.
     */
    public EdictRequest pathMatch(EdictEndpoint endpoint, boolean soft) {
        List<Node> nodes = endpoint.getNodes();
        List<EdictInput> inputs = new ArrayList<>(getInputs());
        int offset = 0;
        int multiplier = nodes.size() + 1;

        while (!nodes.isEmpty() && !inputs.isEmpty()) {
            try {
                String next = inputs.remove(0).into(String.class).orElseThrow();
                Node node = nodes.remove(0);
                offset += node.getDistance(next) * multiplier--;
            } catch (Throwable e) {
                // Couldn't cast endpoint path node to string!
                // This usually means its a parameter for another command with less nodes
                return null;
            }
        }

        if (!soft && !nodes.isEmpty()) {
            return null;
        }

        EdictRequest paired = new EdictRequest(this);
        paired.setPair(endpoint);
        paired.setMatchOffset(offset);
        paired.trimFor(endpoint);

        return paired;
    }

    public void trimFor(EdictEndpoint endpoint) {
        for (String i : endpoint.getPath()) {
            if (inputs.isEmpty()) {
                return;
            }

            inputs.remove(0);
        }
    }
}
