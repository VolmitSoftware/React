package art.arcane.react.api.web;

import art.arcane.react.api.web.dto.ControlItemDto;
import art.arcane.react.api.web.dto.KnobDto;
import art.arcane.react.util.project.registry.Registered;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class RegistryControlBackend<T extends Registered> implements ControlBackend {

    private final String pathPrefix;
    private final Supplier<Collection<T>> itemsSupplier;
    private final Function<String, T> lookup;
    private final Predicate<T> enabledFn;
    private final ControlSerializer serializer;
    private final ControlMutator mutator;

    public RegistryControlBackend(
            String pathPrefix,
            Supplier<Collection<T>> itemsSupplier,
            Function<String, T> lookup,
            Predicate<T> enabledFn,
            ControlSerializer serializer,
            ControlMutator mutator) {
        this.pathPrefix = pathPrefix;
        this.itemsSupplier = itemsSupplier;
        this.lookup = lookup;
        this.enabledFn = enabledFn;
        this.serializer = serializer;
        this.mutator = mutator;
    }

    @Override
    public List<ControlItemDto> list() {
        if (itemsSupplier == null) {
            return new ArrayList<>();
        }
        Collection<T> items = itemsSupplier.get();
        if (items == null) {
            return new ArrayList<>();
        }
        List<ControlItemDto> result = new ArrayList<>();
        for (T item : items) {
            if (item != null) {
                result.add(serializer.toDto(item, enabledFn.test(item)));
            }
        }
        return result;
    }

    @Override
    public ControlItemDto get(String id) {
        T item = lookup.apply(id);
        if (item == null) {
            return null;
        }
        return serializer.toDto(item, enabledFn.test(item));
    }

    @Override
    public ControlItemDto setEnabled(String id, boolean enabled) {
        T item = lookup.apply(id);
        if (item == null) {
            return null;
        }
        if (!mutator.apply(pathPrefix + "." + id + ".enabled", enabled)) {
            throw new IllegalStateException("Failed to apply web control state: " + pathPrefix + "." + id);
        }
        return get(id);
    }

    @Override
    public ControlItemDto setKnobs(String id, Map<String, Object> knobs) {
        T item = lookup.apply(id);
        if (item == null) {
            return null;
        }
        ControlItemDto before = serializer.toDto(item, enabledFn.test(item));
        Map<String, Object> originalValues = new LinkedHashMap<>();
        for (KnobDto knob : before.knobs) {
            originalValues.put(knob.key, knob.value);
        }
        for (String key : knobs.keySet()) {
            if (!originalValues.containsKey(key)) {
                throw new IllegalArgumentException("Unknown web control value: " + pathPrefix + "." + id + "." + key);
            }
        }

        List<String> applied = new ArrayList<>();
        for (Map.Entry<String, Object> entry : knobs.entrySet()) {
            String path = pathPrefix + "." + id + "." + entry.getKey();
            if (!mutator.apply(path, entry.getValue())) {
                rollback(id, originalValues, applied);
                throw new IllegalStateException("Failed to apply web control value: " + path);
            }
            applied.add(entry.getKey());
        }
        return get(id);
    }

    private void rollback(String id, Map<String, Object> originalValues, List<String> applied) {
        for (int index = applied.size() - 1; index >= 0; index--) {
            String key = applied.get(index);
            String path = pathPrefix + "." + id + "." + key;
            if (!mutator.apply(path, originalValues.get(key))) {
                throw new IllegalStateException("Failed to restore web control value after rejection: " + path);
            }
        }
    }
}
