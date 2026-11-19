package art.arcane.react.forge.cost;

import art.arcane.react.mod.cost.CostKind;
import art.arcane.react.mod.cost.CostRegistry;
import art.arcane.react.mod.cost.EventAttribution;
import com.google.common.collect.MapMaker;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.EventListener;
import net.minecraftforge.eventbus.api.listener.ObjBooleanBiConsumer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Times Forge EventBus 7 listeners. Under each bus's own lock the listener records are swapped for copies holding timed
 * callbacks, EventBus builds its invoker from them, and the original records are put back. The backing lists stay
 * pristine, so add and remove keep working; any change invalidates the invoker and the next reconcile wraps it again.
 * Needs the {@code net.minecraftforge.eventbus.internal} package opened to this mod (manifest {@code Add-Opens}).
 */
public final class ForgeEventAttribution implements EventAttribution {
    private static final int SELF_DESTRUCTING = 1;
    private final Map<?, ?> buses;
    private final Method backingList;
    private final Method monitorBackingList;
    private final Method maybeGetInvoker;
    private final Method buildInvoker;
    private final Method invalidateInvoker;
    private final Method shutdownFlag;
    private final Method eventCharacteristics;
    private final Map<Class<?>, RecordShape> shapes = new HashMap<>();
    private final Map<Object, Object> installed = new IdentityHashMap<>();
    private final ConcurrentMap<Object, TimedForgeCallback> callbacks = new MapMaker().weakKeys().makeMap();

    private record RecordShape(Method[] accessors, Constructor<?> constructor, int ownerComponent) {
    }

    public ForgeEventAttribution() throws ReflectiveOperationException {
        Class<?> group = Class.forName("net.minecraftforge.eventbus.internal.BusGroupImpl");
        Class<?> bus = Class.forName("net.minecraftforge.eventbus.internal.AbstractEventBusImpl");
        Object defaultGroup = BusGroup.DEFAULT;
        if (!group.isInstance(defaultGroup)) {
            throw new ClassNotFoundException("Unexpected bus group " + defaultGroup.getClass().getName());
        }
        buses = (Map<?, ?>) accessible(group.getMethod("eventBuses")).invoke(defaultGroup);
        backingList = accessible(bus.getMethod("backingList"));
        monitorBackingList = accessible(bus.getMethod("monitorBackingList"));
        maybeGetInvoker = accessible(bus.getMethod("maybeGetInvoker"));
        buildInvoker = accessible(bus.getMethod("buildInvoker"));
        invalidateInvoker = accessible(bus.getMethod("invalidateInvoker"));
        shutdownFlag = accessible(bus.getMethod("shutdownFlag"));
        eventCharacteristics = accessible(bus.getMethod("eventCharacteristics"));
        for (String name : List.of("ConsumerListener", "PredicateListener", "WrappedConsumerListener", "MonitoringListener")) {
            shape(Class.forName("net.minecraftforge.eventbus.internal.EventListenerImpl$" + name));
        }
    }

    private static Method accessible(Method method) {
        method.setAccessible(true);
        return method;
    }

    private RecordShape shape(Class<?> type) throws ReflectiveOperationException {
        RecordShape shape = shapes.get(type);
        if (shape == null) {
            RecordComponent[] components = type.getRecordComponents();
            if (components == null) {
                throw new NoSuchMethodException(type.getName() + " is not a record");
            }
            Method[] accessors = new Method[components.length];
            Class<?>[] parameters = new Class<?>[components.length];
            int ownerComponent = -1;
            for (int index = 0; index < components.length; index++) {
                accessors[index] = accessible(components[index].getAccessor());
                parameters[index] = components[index].getType();
                if (ownerComponent < 0 && callback(parameters[index])) {
                    ownerComponent = index;
                }
            }
            Constructor<?> constructor = type.getDeclaredConstructor(parameters);
            constructor.setAccessible(true);
            shape = new RecordShape(accessors, constructor, ownerComponent);
            shapes.put(type, shape);
        }
        return shape;
    }

    private static boolean callback(Class<?> type) {
        return type == Consumer.class || type == Predicate.class || type == ObjBooleanBiConsumer.class;
    }

    @Override
    public void install() throws ReflectiveOperationException {
        reconcile();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void reconcile() throws ReflectiveOperationException {
        for (Object bus : new ArrayList<>(buses.values())) {
            if ((((int) eventCharacteristics.invoke(bus)) & SELF_DESTRUCTING) != 0 || ((AtomicBoolean) shutdownFlag.invoke(bus)).get()) {
                continue;
            }
            List<EventListener> listeners = (List<EventListener>) backingList.invoke(bus);
            synchronized (listeners) {
                List<EventListener> monitors = (List<EventListener>) monitorBackingList.invoke(bus);
                if (listeners.isEmpty() && monitors.isEmpty()) {
                    continue;
                }
                Object current = maybeGetInvoker.invoke(bus);
                if (current != null && current == installed.get(bus)) {
                    continue;
                }
                Map<EventListener, EventListener> originals = new IdentityHashMap<>();
                try {
                    swap(listeners, originals);
                    swap(monitors, originals);
                    installed.put(bus, buildInvoker.invoke(bus));
                } finally {
                    restore(listeners, originals);
                    restore(monitors, originals);
                }
            }
        }
    }

    private void swap(List<EventListener> listeners, Map<EventListener, EventListener> originals) throws ReflectiveOperationException {
        for (int index = 0; index < listeners.size(); index++) {
            EventListener original = listeners.get(index);
            EventListener timed = timed(original);
            if (timed != original) {
                originals.put(timed, original);
                listeners.set(index, timed);
            }
        }
    }

    private static void restore(List<EventListener> listeners, Map<EventListener, EventListener> originals) {
        for (int index = 0; index < listeners.size(); index++) {
            EventListener original = originals.get(listeners.get(index));
            if (original != null) {
                listeners.set(index, original);
            }
        }
    }

    private EventListener timed(EventListener listener) throws ReflectiveOperationException {
        if (!listener.getClass().isRecord()) {
            return listener;
        }
        RecordShape shape = shape(listener.getClass());
        if (shape.ownerComponent() < 0) {
            return listener;
        }
        Object[] values = new Object[shape.accessors().length];
        for (int index = 0; index < values.length; index++) {
            values[index] = shape.accessors()[index].invoke(listener);
        }
        Class<?> owner = ownerClass(values[shape.ownerComponent()]);
        for (int index = 0; index < values.length; index++) {
            if (callback(shape.accessors()[index].getReturnType()) && values[index] != null) {
                values[index] = callback(values[index], owner);
            }
        }
        return (EventListener) shape.constructor().newInstance(values);
    }

    private TimedForgeCallback callback(Object delegate, Class<?> owner) {
        TimedForgeCallback timed = callbacks.get(delegate);
        if (timed == null) {
            timed = new TimedForgeCallback(delegate, CostRegistry.bucketFor(owner, CostKind.EVENT));
            callbacks.put(delegate, timed);
        }
        return timed;
    }

    /** Callbacks built inside EventBus (monitor adapters) capture the real listener; follow it once. */
    private static Class<?> ownerClass(Object callback) {
        Class<?> type = callback.getClass();
        if (type.getModule() != EventListener.class.getModule()) {
            return type;
        }
        for (Field field : type.getDeclaredFields()) {
            if (field.getType().isPrimitive()) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object captured = field.get(callback);
                if (captured != null && captured.getClass().getModule() != EventListener.class.getModule()) {
                    return captured.getClass();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return type;
            }
        }
        return type;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void uninstall() throws ReflectiveOperationException {
        for (Map.Entry<Object, Object> entry : installed.entrySet()) {
            Object bus = entry.getKey();
            List<EventListener> listeners = (List<EventListener>) backingList.invoke(bus);
            synchronized (listeners) {
                if (maybeGetInvoker.invoke(bus) == entry.getValue()) {
                    invalidateInvoker.invoke(bus);
                }
            }
        }
        installed.clear();
    }
}
