package art.arcane.react.mod.cost;

import net.minecraft.server.level.TicketType;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The class that called the ticket type factory, for versions whose ticket types are not registered. Recorded once per
 * ticket type, mostly while {@link TicketType} and mod classes initialise, so this class stays free of other game types.
 */
public final class TicketTypeCreators {
    private static final Map<Object, Class<?>> CREATORS = Collections.synchronizedMap(new IdentityHashMap<>());

    private TicketTypeCreators() {
    }

    public static void createdByCaller(Object type) {
        if (type == null) {
            return;
        }
        Optional<Class<?>> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk((Stream<StackWalker.StackFrame> frames) -> frames.<Class<?>>map(StackWalker.StackFrame::getDeclaringClass)
                        .filter((Class<?> frame) -> frame != TicketType.class && frame != TicketTypeCreators.class)
                        .findFirst());
        caller.ifPresent((Class<?> creator) -> CREATORS.putIfAbsent(type, creator));
    }

    static Class<?> creator(Object type) {
        return CREATORS.get(type);
    }
}
