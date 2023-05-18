package art.arcane.edict.api.parser;

import art.arcane.edict.api.Confidence;

/**
 * Represents a parser which can take text and parse it into an object of a specific type
 * @param <T> the type of object to parse into
 */
@FunctionalInterface
public interface EdictParser<T> {
    EdictValue<T> parse(String s);

    default EdictValue<T> of(T value) {
        return of(value, Confidence.LOW);
    }

    default EdictValue<T> of(T value, Confidence c) {
        return EdictValue.<T>builder()
            .value(value)
            .confidence(c)
            .build();
    }

    default EdictValue<T> high(T value) {
        return of(value, Confidence.HIGH);
    }

    default EdictValue<T> low(T value) {
        return of(value, Confidence.LOW);
    }

    default EdictValue<T> invalid(T value) {
        return of(value, Confidence.INVALID);
    }
}
