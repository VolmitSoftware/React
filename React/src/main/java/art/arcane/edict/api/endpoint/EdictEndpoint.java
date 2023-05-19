package art.arcane.edict.api.endpoint;

import art.arcane.edict.api.context.EdictContext;
import art.arcane.edict.api.field.EdictField;
import art.arcane.edict.api.request.EdictResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

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
     * The command to be executed.
     */
    private String command;

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
    public String getName(){
        List<String> path = getPath();
        return path.get(path.size() - 1);
    }

    /**
     * Returns the path of the command, where each segment is an element in the list.
     *
     * @return A list of command segments.
     */
    public List<String> getPath() {
        return Arrays.stream(command.split("\\Q \\E")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }
}
