package art.arcane.edict.api.request;

import art.arcane.edict.Edict;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class Node {
    @Getter
    private List<String> aliases;

    public Node(List<String> aliases) {
        this.aliases = aliases;
    }

    public boolean matches(String from) {
        return aliases.stream().anyMatch(i -> i.equalsIgnoreCase(from));
    }

    public boolean matches(Node from) {
        return aliases.stream().anyMatch(i -> from.getAliases().stream().anyMatch(i::equalsIgnoreCase));
    }

    public int getDistance(String from) {
        return matches(from) ? 0 : aliases.stream().mapToInt((e) -> Edict.calculateLevenshteinDistance(from, e)).min().orElse(0);
    }

    @Override
    public String toString() {
        return aliases.get(0);
    }
}
