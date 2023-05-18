package art.arcane.edict.api.endpoint;

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

@AllArgsConstructor
@Builder
@Data
public class EdictEndpoint {
    private String command;
    private Consumer<EdictResponse> executor;

    @Singular
    private List<EdictField> fields;

    public List<String> getParentPath() {
        List<String> path = getPath();
        path.remove(0);
        return path;
    }

    public String getName(){
        List<String> path = getPath();
        return path.get(path.size() - 1);
    }

    public List<String> getPath() {
        return Arrays.stream(command.split("\\Q \\E")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }
}
