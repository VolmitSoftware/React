package art.arcane.edict.api.parser;

import art.arcane.edict.Edict;

import java.util.Comparator;
import java.util.List;

public interface SelectionParser<T> extends EdictParser<T>, Suggestive {
    private static int calculateSimilarity(String enumConstantName, String targetString) {
        return Edict.calculateLevenshteinDistance(enumConstantName, targetString);
    }

    default EdictValue<T> parse(String s) {
        return getSelectionOptions().stream()
                .min(Comparator.comparingInt(enumConstant -> calculateSimilarity(getName(enumConstant), s.trim())))
                .map(this::high)
                .orElse(low(getDefault()));
    }

    List<T> getSelectionOptions();

    default T getDefault() {
        return getSelectionOptions().get(0);
    }

    String getName(T t);

    default List<String> getOptions() {
        return getSelectionOptions().stream()
                .map(this::getName)
                .toList();
    }

    default boolean isMandatory() {
        return true;
    }
}
