package art.arcane.react.api.web;

import art.arcane.react.api.action.Action;
import art.arcane.react.api.web.dto.ActionDescriptorDto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class RegistryActionBackend implements ActionBackend {

    private final Supplier<Collection<Action<?>>> actionsSupplier;
    private final Function<String, Action<?>> lookup;
    private final ActionParamSerializer serializer;

    public RegistryActionBackend(
            Supplier<Collection<Action<?>>> actionsSupplier,
            Function<String, Action<?>> lookup,
            ActionParamSerializer serializer) {
        this.actionsSupplier = actionsSupplier;
        this.lookup = lookup;
        this.serializer = serializer;
    }

    @Override
    public List<ActionDescriptorDto> list() {
        Collection<Action<?>> actions = actionsSupplier.get();
        if (actions == null) {
            return new ArrayList<>();
        }
        List<ActionDescriptorDto> result = new ArrayList<>();
        for (Action<?> action : actions) {
            if (action == null) {
                continue;
            }
            if (ReactActionCatalog.EXCLUDED_IDS.contains(action.getId())) {
                continue;
            }
            ActionDescriptorDto dto = serializer.describe(action);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    @Override
    public ActionDescriptorDto get(String id) {
        if (id == null || ReactActionCatalog.EXCLUDED_IDS.contains(id)) {
            return null;
        }
        Action<?> action = lookup.apply(id);
        if (action == null) {
            return null;
        }
        return serializer.describe(action);
    }
}
