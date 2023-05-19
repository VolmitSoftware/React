package art.arcane.edict.api.parser;

import art.arcane.edict.api.Confidence;

/**
 * Functional interface that defines the contract for a parser in the Edict command handling system.
 * <p>
 * An implementation of this interface is responsible for parsing a String into an object of type T, and
 * producing an {@link EdictValue} that encapsulates both the parsed object and an associated confidence level.
 * The interface provides several default methods to conveniently produce EdictValue instances with
 * specific confidence levels.
 *
 * @param <T> The type of the object that the parser is expected to parse into.
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
