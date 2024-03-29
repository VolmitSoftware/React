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

package art.arcane.edict.api.endpoint;

import art.arcane.edict.api.SenderType;
import art.arcane.edict.api.context.EdictContext;
import art.arcane.edict.api.field.EdictField;
import art.arcane.edict.api.request.Node;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The EdictEndpoint class represents a command endpoint in an Edict command handling system.
 * It encapsulates the command itself, a method to execute the command, and a list of fields
 * which can be populated with input values when the command is executed.
 * <br><br>
 * The class also provides methods for getting the command path and the name of the command,
 * derived from the command string itself.
 */
@AllArgsConstructor
@Builder
@Data
public class EdictEndpoint {
    /**
     * The command to be executed. Format just the header not the parameters such as
     * "/plugin subcommand"
     */
    private String command;

    /**
     * The description of the command. This is used in the help command.
     */
    @Builder.Default
    private String description = "No Description";

    /**
     * Additional command references as aliases. These still require the full command format
     * such as "/plugin altcommand"
     */
    @Singular
    private List<String> aliases;

    /**
     * Who is allowed to execute this command. Players or Consoles. Defaults to ANY
     */
    @Builder.Default
    private SenderType senderType = SenderType.ANY;

    /**
     * The permission node(s) required to execute this command.
     */
    @Singular
    private List<String> permissions;

    /**
     * The action to be performed when this command is executed.
     */
    private Consumer<EdictContext> executor;

    /**
     * The list of fields which can be populated with input values when the command is executed.
     */
    @Singular
    private List<EdictField> fields;

    /**
     * Returns the path of the command excluding the first element.
     *
     * @return A list of command segments excluding the first element.
     */
    public List<String> getParentPath() {
        List<String> path = getPath();
        path.remove(0);
        return path;
    }

    /**
     * Returns the name of the command, which is the last segment of the command path.
     *
     * @return The name of the command.
     */
    public String getName() {
        List<String> path = getPath();
        return path.get(path.size() - 1);
    }

    /**
     * Returns the path of the command, where each segment is an element in the list.
     *
     * @return A list of command segments.
     */
    public List<String> getPath() {
        return getPath(getCommand());
    }

    public List<Node> getNodes() {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < getPath().size(); i++) {
            List<String> s = new ArrayList<>();
            s.add(getPath().get(i));

            for (String j : getAliases()) {
                s.add(getPath(j).get(i));
            }

            nodes.add(new Node(s));
        }

        return nodes;
    }

    public List<String> getPath(String c) {
        return Arrays.stream(c.split("\\Q \\E")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    public List<List<String>> getPaths() {
        List<List<String>> l = new ArrayList<>();
        l.add(getPath());

        if (getAliases() != null) {
            for (String i : getAliases()) {
                l.add(getPath(i));
            }
        }

        return l;
    }

    public String getUsage() {
        return getCommand() + " " + getFields().stream().map(EdictField::getUsage).collect(Collectors.joining(" "));
    }
}
