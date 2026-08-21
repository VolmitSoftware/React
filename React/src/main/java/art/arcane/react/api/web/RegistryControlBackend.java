package art.arcane.react.api.web;

import art.arcane.react.api.web.dto.ControlItemDto;
import art.arcane.react.util.project.registry.Registered;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
        mutator.apply(pathPrefix + "." + id + ".enabled", enabled);
        return get(id);
    }

    @Override
    public ControlItemDto setKnobs(String id, Map<String, Object> knobs) {
        T item = lookup.apply(id);
        if (item == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : knobs.entrySet()) {
            mutator.apply(pathPrefix + "." + id + "." + entry.getKey(), entry.getValue());
        }
        return get(id);
    }
}
