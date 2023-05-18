package art.arcane.edict.api.input;

import art.arcane.edict.api.Confidence;
import art.arcane.edict.api.parser.EdictValue;

import java.util.List;
import java.util.Optional;

public interface EdictInput {
    List<EdictValue<?>> getParsedValues();

    default List<EdictValue<?>> getParsedValues(Confidence minimumConfidence) {
        return getParsedValues().stream().filter(i -> i.getConfidence().ordinal() >= minimumConfidence.ordinal()).toList();
    }

    default <T> Optional<T> into(){
        return into(Confidence.LOW);
    }

    @SuppressWarnings("unchecked")
    default <T> Optional<T> into(Confidence minimumConfidence) {
        for(EdictValue<?> edictValue : getParsedValues(minimumConfidence)) {
            if(edictValue.getConfidence() == Confidence.HIGH) {
                try {
                    return Optional.of((T) edictValue.getValue());
                }

                catch(Throwable ignored) {

                }
            }
        }

        return Optional.empty();
    }
}
