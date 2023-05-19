package art.arcane.edict.api.context;

import java.util.Optional;

@FunctionalInterface
public interface EdictContextResolver<T> {
    Optional<T> resolve(EdictContext context);
}
