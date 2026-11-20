package art.arcane.react.forge.cost;

import art.arcane.react.mod.cost.CostKind;
import art.arcane.react.mod.cost.CostRegistry;
import art.arcane.react.mod.cost.EventAttribution;
import com.google.common.collect.MapMaker;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.ListenerList;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.IEventListener;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Times Forge EventBus 6 listeners on the game bus by swapping each listener list's flattened cache for an array of
 * timed wrappers. The per-priority source lists are never touched, so register and unregister keep working: any change
 * rebuilds a clean cache and the next reconcile wraps it again. Priority markers stay in place for phase tracking.
 */
public final class ForgeEventAttribution implements EventAttribution {
    private final EventBus bus;
    private final int busId;
    private final Field registrations;
    private final Field allLists;
    private final Method instance;
    private final CacheSlot cache;
    private final Method forceRebuild;
    private final Map<Object, IEventListener[]> installed = new IdentityHashMap<>();
    private final ConcurrentMap<IEventListener, TimedForgeCallback> wrappers = new MapMaker().weakKeys().makeMap();
    private final Map<IEventListener, Class<?>> owners = new IdentityHashMap<>();
    private boolean ownersFresh;

    public ForgeEventAttribution(IEventBus gameBus) throws ReflectiveOperationException {
        if (!(gameBus instanceof EventBus eventBus)) {
            throw new ClassNotFoundException("Unexpected game bus " + gameBus.getClass().getName());
        }
        bus = eventBus;
        busId = accessible(EventBus.class.getDeclaredField("busID")).getInt(eventBus);
        registrations = accessible(EventBus.class.getDeclaredField("listeners"));
        allLists = accessible(ListenerList.class.getDeclaredField("allLists"));
        instance = ListenerList.class.getDeclaredMethod("getInstance", int.class);
        instance.setAccessible(true);
        Class<?> inst = Class.forName("net.minecraftforge.eventbus.ListenerList$ListenerListInst");
        cache = cacheSlot(inst);
        forceRebuild = inst.getDeclaredMethod("forceRebuild");
        forceRebuild.setAccessible(true);
    }

    /**
     * EventBus 6.0 (Forge 47, Minecraft 1.20.1) keeps the flattened cache in an {@code AtomicReference}; later 6.x
     * releases keep the array itself in the field.
     */
    private static CacheSlot cacheSlot(Class<?> inst) throws ReflectiveOperationException {
        Field field = inst.getDeclaredField("listeners");
        if (field.getType() == AtomicReference.class) {
            accessible(field);
            return (Object owner, IEventListener[] expected, IEventListener[] replacement) -> {
                @SuppressWarnings("unchecked")
                AtomicReference<IEventListener[]> reference = (AtomicReference<IEventListener[]>) field.get(owner);
                return reference.compareAndSet(expected, replacement);
            };
        }
        VarHandle handle = MethodHandles.privateLookupIn(inst, MethodHandles.lookup()).findVarHandle(inst, "listeners", IEventListener[].class);
        return (Object owner, IEventListener[] expected, IEventListener[] replacement) -> handle.compareAndSet(owner, expected, replacement);
    }

    @FunctionalInterface
    private interface CacheSlot {
        boolean swap(Object owner, IEventListener[] expected, IEventListener[] replacement) throws ReflectiveOperationException;
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
        List<ListenerList> lists;
        synchronized (ListenerList.class) {
            lists = new ArrayList<>((List<ListenerList>) allLists.get(null));
        }
        ownersFresh = false;
        for (ListenerList list : lists) {
            Object inst = instance.invoke(list, busId);
            IEventListener[] current = list.getListeners(busId);
            if (current == null || current.length == 0 || current == installed.get(inst)) {
                continue;
            }
            IEventListener[] wrapped = wrap(current);
            if (cache.swap(inst, current, wrapped)) {
                installed.put(inst, wrapped);
            }
        }
    }

    @Override
    public void uninstall() throws ReflectiveOperationException {
        for (Object inst : installed.keySet()) {
            forceRebuild.invoke(inst);
        }
        installed.clear();
        owners.clear();
    }

    private IEventListener[] wrap(IEventListener[] listeners) throws ReflectiveOperationException {
        IEventListener[] wrapped = new IEventListener[listeners.length];
        for (int index = 0; index < listeners.length; index++) {
            IEventListener listener = listeners[index];
            if (listener instanceof TimedForgeCallback || listener instanceof EventPriority) {
                wrapped[index] = listener;
                continue;
            }
            TimedForgeCallback timed = wrappers.get(listener);
            if (timed == null) {
                timed = new TimedForgeCallback(listener, CostRegistry.bucketFor(ownerClass(listener), CostKind.EVENT));
                wrappers.put(listener, timed);
            }
            wrapped[index] = timed;
        }
        return wrapped;
    }

    @SuppressWarnings("unchecked")
    private Class<?> ownerClass(IEventListener listener) throws ReflectiveOperationException {
        Class<?> owner = owners.get(listener);
        if (owner == null && !ownersFresh) {
            ownersFresh = true;
            owners.clear();
            Map<Object, List<IEventListener>> registered = (Map<Object, List<IEventListener>>) registrations.get(bus);
            for (Map.Entry<Object, List<IEventListener>> entry : registered.entrySet()) {
                Object target = entry.getKey();
                Class<?> targetClass = target instanceof Class<?> type ? type : target.getClass();
                synchronized (entry.getValue()) {
                    for (IEventListener registeredListener : entry.getValue()) {
                        owners.put(registeredListener, targetClass);
                    }
                }
            }
            owner = owners.get(listener);
        }
        return owner == null ? listener.getClass() : owner;
    }
}
