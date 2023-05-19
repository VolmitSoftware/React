package art.arcane.edict.api.context;

import art.arcane.edict.Edict;
import art.arcane.edict.api.endpoint.EdictEndpoint;
import art.arcane.edict.api.request.EdictRequest;
import art.arcane.edict.api.request.EdictResponse;
import it.unimi.dsi.fastutil.Hash;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * The `EdictContext` class stores and manages the state of an Edict operation.
 * It holds important information such as the Edict object, the endpoint, the request, and the response of the operation.
 * It also provides a mechanism for resolving context-based dependencies through the use of `resolve` method.
 * Thread-local instances of the `EdictContext` can be managed through the `put` and `get` static methods, providing a way to ensure thread safety.
 *
 * @see Edict
 * @see EdictEndpoint
 * @see EdictRequest
 * @see EdictResponse
 */
@Data
@Builder
@AllArgsConstructor
public class EdictContext {

    /**
     * A map linking thread IDs to their corresponding `EdictContext` instances.
     * This is used for thread-local storage of `EdictContext`.
     */
    private static HashMap<Long, EdictContext> threadContext = new HashMap<>();

    /**
     * The `Edict` object associated with this context.
     */
    private transient Edict edict;

    /**
     * The `EdictEndpoint` associated with this context.
     */
    private transient EdictEndpoint endpoint;

    /**
     * The `EdictRequest` associated with this context.
     */
    private transient EdictRequest request;

    /**
     * The `EdictResponse` associated with this context.
     */
    private EdictResponse response;

    /**
     * The `CommandSender` associated with this context.
     */
    private transient CommandSender sender;

    /**
     * A map for storing resolved objects by their class types.
     * It's used in the `resolve` method to cache and reuse resolved objects.
     */
    private Map<Class<?>, Object> resolved = new HashMap<>();

    /**
     * Returns the player associated with the context, if any.
     *
     * @return the associated player or null if the sender is not a player.
     */
    public Player getPlayer() {
        return sender instanceof Player p ? p : null;
    }

    /**
     * Resolves an object of the given class type using context resolvers.
     *
     * @param resolver The class of the object to resolve.
     * @return The resolved object or null if no resolver could be found for the given class.
     */
    @SuppressWarnings("unchecked")
    public <T> T resolve(Class<?> resolver) {
        Object o = resolved.get(resolver);

        if(o != null) {
            return (T) o;
        }

        return (T) edict.getContextResolvers().stream().filter((e) -> e.getClass().equals(resolver)).findFirst()
            .map(i -> resolved.computeIfAbsent(i.getClass(), (e) -> i.resolve(this))).orElse(null);
    }

    /**
     * Associates the given `EdictContext` with the current thread.
     *
     * @param c The `EdictContext` to associate with the current thread.
     * @return The previous `EdictContext` associated with the current thread, or null if there was none.
     */
    public static EdictContext put(EdictContext c){
        return threadContext.put(Thread.currentThread().getId(), c);
    }

    /**
     * Retrieves the `EdictContext` associated with the current thread.
     *
     * @return The `EdictContext` associated with the current thread, or null if there is none.
     */
    public static EdictContext get() {
        return threadContext.get(Thread.currentThread().getId());
    }
}
