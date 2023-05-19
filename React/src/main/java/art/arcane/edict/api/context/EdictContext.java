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

@Data
@Builder
@AllArgsConstructor
public class EdictContext {
    private static HashMap<Long, EdictContext> threadContext = new HashMap<>();

    private Edict edict;
    private EdictEndpoint endpoint;
    private EdictRequest request;
    private EdictResponse response;
    private CommandSender sender;
    private Map<Class<?>, Object> resolved = new HashMap<>();

    public Player getPlayer() {
        return sender instanceof Player p ? p : null;
    }

    @SuppressWarnings("unchecked")
    public <T> T resolve(Class<?> resolver) {
        Object o = resolved.get(resolver);

        if(o != null) {
            return (T) o;
        }

        return (T) edict.getContextResolvers().stream().filter((e) -> e.getClass().equals(resolver)).findFirst()
            .map(i -> resolved.computeIfAbsent(i.getClass(), (e) -> i.resolve(this))).orElse(null);
    }

    public static EdictContext put(EdictContext c){
        return threadContext.put(Thread.currentThread().getId(), c);
    }

    public static EdictContext get() {
        return threadContext.get(Thread.currentThread().getId());
    }
}
