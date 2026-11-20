package art.arcane.react.fabric.cost;

import art.arcane.react.mod.cost.CostBucket;
import art.arcane.react.mod.cost.CostFrame;
import art.arcane.react.mod.cost.CostKind;
import art.arcane.react.mod.cost.CostRegistry;
import art.arcane.react.mod.cost.EventAttribution;
import com.google.common.collect.MapMaker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.impl.base.event.EventFactoryImpl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentMap;

/**
 * Times Fabric API event listeners. A mixin on {@code ArrayBackedEvent.update()} hands the handler array to
 * {@link #wrapArray} before the event's invoker factory sees it; the registered handlers and phases are never changed.
 * Turning attribution on or off rebuilds every invoker through {@link EventFactoryImpl#invalidate()}.
 */
public final class FabricEventWrap implements EventAttribution {
    public static volatile boolean active;
    private static final ConcurrentMap<Object, Object> WRAPPERS = new MapMaker().weakKeys().makeMap();
    private static final MethodHandle ENTER;
    private static final MethodHandle EXIT_VOID;
    private static final MethodHandle EXIT_VALUE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            ENTER = lookup.findStatic(CostFrame.class, "enter", MethodType.methodType(long.class));
            EXIT_VOID = lookup.findStatic(FabricEventWrap.class, "exitVoid", MethodType.methodType(void.class, Throwable.class, long.class, CostBucket.class));
            EXIT_VALUE = lookup.findStatic(FabricEventWrap.class, "exitValue", MethodType.methodType(Object.class, Throwable.class, Object.class, long.class, CostBucket.class));
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @Override
    public void install() {
        active = true;
        EventFactoryImpl.invalidate();
    }

    @Override
    public void reconcile() {
    }

    @Override
    public void uninstall() {
        active = false;
        EventFactoryImpl.invalidate();
    }

    public static Object wrapArray(Object handlers) {
        if (!active || handlers == null) {
            return handlers;
        }
        Class<?> type = handlers.getClass().getComponentType();
        int length = Array.getLength(handlers);
        Object wrapped = Array.newInstance(type, length);
        for (int index = 0; index < length; index++) {
            Array.set(wrapped, index, wrap(type, Array.get(handlers, index)));
        }
        return wrapped;
    }

    private static Object wrap(Class<?> type, Object listener) {
        if (listener == null) {
            return null;
        }
        Object cached = WRAPPERS.get(listener);
        if (type.isInstance(cached)) {
            return cached;
        }
        CostBucket bucket = CostRegistry.bucketFor(listener.getClass(), CostKind.EVENT);
        Object timed = fast(type, listener, bucket);
        if (timed == null) {
            timed = proxy(type, listener, bucket);
        }
        if (timed == null) {
            return listener;
        }
        WRAPPERS.put(listener, timed);
        return timed;
    }

    private static Object fast(Class<?> type, Object listener, CostBucket bucket) {
        if (type == ServerTickEvents.EndTick.class) {
            ServerTickEvents.EndTick delegate = (ServerTickEvents.EndTick) listener;
            return (ServerTickEvents.EndTick) server -> {
                long start = CostFrame.enter();
                try {
                    delegate.onEndTick(server);
                } finally {
                    CostFrame.exit(start, bucket);
                }
            };
        }
        if (type == ServerTickEvents.StartTick.class) {
            ServerTickEvents.StartTick delegate = (ServerTickEvents.StartTick) listener;
            return (ServerTickEvents.StartTick) server -> {
                long start = CostFrame.enter();
                try {
                    delegate.onStartTick(server);
                } finally {
                    CostFrame.exit(start, bucket);
                }
            };
        }
        return FabricLevelTicks.wrap(type, listener, bucket);
    }

    private static Object proxy(Class<?> type, Object listener, CostBucket bucket) {
        if (!type.isInterface() || !Modifier.isPublic(type.getModifiers())) {
            return null;
        }
        Method single = null;
        for (Method method : type.getMethods()) {
            if (Modifier.isAbstract(method.getModifiers())) {
                if (single != null) {
                    return null;
                }
                single = method;
            }
        }
        if (single == null) {
            return null;
        }
        try {
            MethodHandle target = MethodHandles.publicLookup().unreflect(single).bindTo(listener);
            Class<?> returnType = target.type().returnType();
            MethodHandle cleanup;
            if (returnType == void.class) {
                cleanup = MethodHandles.insertArguments(EXIT_VOID, 2, bucket);
            } else {
                cleanup = MethodHandles.insertArguments(EXIT_VALUE, 3, bucket)
                        .asType(MethodType.methodType(returnType, Throwable.class, returnType, long.class));
            }
            MethodHandle timed = MethodHandles.tryFinally(MethodHandles.dropArguments(target, 0, long.class), cleanup);
            return MethodHandleProxies.asInterfaceInstance(type, MethodHandles.foldArguments(timed, ENTER));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    private static void exitVoid(Throwable failure, long start, CostBucket bucket) {
        CostFrame.exit(start, bucket);
    }

    private static Object exitValue(Throwable failure, Object result, long start, CostBucket bucket) {
        CostFrame.exit(start, bucket);
        return result;
    }
}
