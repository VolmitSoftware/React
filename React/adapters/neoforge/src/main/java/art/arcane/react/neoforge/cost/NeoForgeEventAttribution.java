package art.arcane.react.neoforge.cost;

import art.arcane.react.mod.cost.CostKind;
import art.arcane.react.mod.cost.CostRegistry;
import art.arcane.react.mod.cost.EventAttribution;
import com.google.common.collect.MapMaker;
import net.neoforged.bus.ConsumerEventHandler;
import net.neoforged.bus.EventBus;
import net.neoforged.bus.IWrapperListener;
import net.neoforged.bus.ListenerList;
import net.neoforged.bus.api.EventListener;
import net.neoforged.bus.api.IEventBus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Times NeoForge game bus listeners (bus 8) by swapping each listener list's flattened cache for an array of timed
 * wrappers. The per-priority source lists are never touched, so register and unregister keep working: any change
 * rebuilds a clean cache and the next reconcile wraps it again.
 */
public final class NeoForgeEventAttribution implements EventAttribution {
    private final EventBus bus;
    private final Field registrations;
    private final Field listenerLists;
    private final Method readMap;
    private final Field cache;
    private final Field phaseCache;
    private final Method forceRebuild;
    private final Field consumer;
    private final Map<ListenerList, EventListener[]> installed = new IdentityHashMap<>();
    private final ConcurrentMap<EventListener, TimedNeoListener> wrappers = new MapMaker().weakKeys().makeMap();
    private final Map<EventListener, Class<?>> owners = new IdentityHashMap<>();
    private boolean ownersFresh;

    public NeoForgeEventAttribution(IEventBus gameBus) throws ReflectiveOperationException {
        if (!(gameBus instanceof EventBus eventBus)) {
            throw new ClassNotFoundException("Unexpected game bus " + gameBus.getClass().getName());
        }
        bus = eventBus;
        registrations = accessible(EventBus.class.getDeclaredField("listeners"));
        listenerLists = accessible(EventBus.class.getDeclaredField("listenerLists"));
        readMap = listenerLists.getType().getDeclaredMethod("getReadMap");
        readMap.setAccessible(true);
        cache = accessible(ListenerList.class.getDeclaredField("listeners"));
        phaseCache = accessible(ListenerList.class.getDeclaredField("perPhaseListeners"));
        forceRebuild = ListenerList.class.getDeclaredMethod("forceRebuild");
        forceRebuild.setAccessible(true);
        consumer = accessible(ConsumerEventHandler.class.getDeclaredField("consumer"));
    }

    private static Field accessible(Field field) {
        field.setAccessible(true);
        return field;
    }

    @Override
    public void install() throws ReflectiveOperationException {
        reconcile();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void reconcile() throws ReflectiveOperationException {
        Map<Class<?>, ListenerList> lists = (Map<Class<?>, ListenerList>) readMap.invoke(listenerLists.get(bus));
        ownersFresh = false;
        for (ListenerList list : lists.values()) {
            EventListener[] current = list.getListeners();
            if (current == null || current.length == 0 || current == installed.get(list)) {
                continue;
            }
            EventListener[] wrapped = wrap(current);
            AtomicReference<EventListener[]> reference = (AtomicReference<EventListener[]>) cache.get(list);
            if (reference.compareAndSet(current, wrapped)) {
                installed.put(list, wrapped);
                AtomicReference<EventListener[][]> phases = (AtomicReference<EventListener[][]>) phaseCache.get(list);
                EventListener[][] perPhase = phases.get();
                if (perPhase != null) {
                    EventListener[][] wrappedPhases = new EventListener[perPhase.length][];
                    for (int phase = 0; phase < perPhase.length; phase++) {
                        wrappedPhases[phase] = wrap(perPhase[phase]);
                    }
                    phases.compareAndSet(perPhase, wrappedPhases);
                }
            }
        }
    }

    @Override
    public void uninstall() throws ReflectiveOperationException {
        for (ListenerList list : installed.keySet()) {
            forceRebuild.invoke(list);
        }
        installed.clear();
        owners.clear();
    }

    private EventListener[] wrap(EventListener[] listeners) throws ReflectiveOperationException {
        EventListener[] wrapped = new EventListener[listeners.length];
        for (int index = 0; index < listeners.length; index++) {
            EventListener listener = listeners[index];
            if (listener instanceof TimedNeoListener) {
                wrapped[index] = listener;
                continue;
            }
            TimedNeoListener timed = wrappers.get(listener);
            if (timed == null) {
                timed = new TimedNeoListener(listener, CostRegistry.bucketFor(ownerClass(listener), CostKind.EVENT));
                wrappers.put(listener, timed);
            }
            wrapped[index] = timed;
        }
        return wrapped;
    }

    @SuppressWarnings("unchecked")
    private Class<?> ownerClass(EventListener listener) throws ReflectiveOperationException {
        Class<?> owner = owners.get(listener);
        if (owner == null && !ownersFresh) {
            ownersFresh = true;
            owners.clear();
            Map<Object, List<EventListener>> registered = (Map<Object, List<EventListener>>) registrations.get(bus);
            for (Map.Entry<Object, List<EventListener>> entry : registered.entrySet()) {
                Object target = entry.getKey();
                Class<?> targetClass = target instanceof Class<?> type ? type : target instanceof Method method ? method.getDeclaringClass() : target.getClass();
                synchronized (entry.getValue()) {
                    for (EventListener registeredListener : entry.getValue()) {
                        owners.put(registeredListener, targetClass);
                        if (registeredListener instanceof IWrapperListener wrapper) {
                            owners.put(wrapper.getWithoutCheck(), targetClass);
                        }
                    }
                }
            }
            owner = owners.get(listener);
        }
        if (owner == null && listener instanceof ConsumerEventHandler) {
            owner = consumer.get(listener).getClass();
        }
        return owner == null ? listener.getClass() : owner;
    }
}
