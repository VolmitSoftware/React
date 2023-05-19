package art.arcane.edict.api.context;

import java.util.Optional;

/**
 * The `EdictContextResolver` is a functional interface used for resolving objects of a certain type
 * within a given `EdictContext`. An implementation of this interface should provide logic to extract or
 * create the desired object from the provided context.
 * <p>
 * This interface can be used to provide a flexible mechanism for retrieving or creating contextual objects
 * in an `Edict` operation.
 *
 * @param <T> the type of object to resolve from the context
 *
 * @see EdictContext
 */
@FunctionalInterface
public interface EdictContextResolver<T> {
    /**
     * This method is intended to be implemented to resolve an object of type T from a given `EdictContext`.
     * The method should return an Optional containing the resolved object if it could be successfully resolved,
     * or an empty Optional if it could not.
     *
     * @param context the `EdictContext` from which to resolve the object
     * @return an Optional containing the resolved object if it could be successfully resolved, or an empty Optional if not
     */
    Optional<T> resolve(EdictContext context);
}
