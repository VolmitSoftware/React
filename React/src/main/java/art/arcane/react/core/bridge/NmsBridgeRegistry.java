package art.arcane.react.core.bridge;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class NmsBridgeRegistry {
    private static final java.util.Set<String> warnedIds = ConcurrentHashMap.newKeySet();

    private final ConcurrentHashMap<String, NmsBridgeHandle> handles = new ConcurrentHashMap<>();
    private volatile MappingsLoader mappingsLoader;

    public void setMappingsLoader(MappingsLoader loader) {
        this.mappingsLoader = loader;
    }

    public NmsBridgeHandle resolve(NmsBridgeDescriptor descriptor) {
        return handles.computeIfAbsent(descriptor.logicalId(), id -> doResolve(descriptor));
    }

    public void clear() {
        handles.clear();
    }

    public Collection<NmsBridgeHandle> allHandles() {
        return List.copyOf(handles.values());
    }

    public BridgeHealthReport snapshotHealth() {
        List<BridgeHealthReport.BridgeHealthEntry> entries = new java.util.ArrayList<>();
        for (NmsBridgeHandle handle : handles.values()) {
            BridgeResolution r = handle.resolution();
            entries.add(new BridgeHealthReport.BridgeHealthEntry(
                    r.logicalId(), r.kind(), r.available(), r.resolutionSummary(), r.failureReason()));
        }
        return new BridgeHealthReport(List.copyOf(entries));
    }

    public void warnUnavailable(java.util.function.Consumer<String> logger) {
        for (NmsBridgeHandle handle : handles.values()) {
            BridgeResolution r = handle.resolution();
            if (!r.available() && warnedIds.add(r.logicalId())) {
                logger.accept("[React] NMS bridge unavailable: " + r.logicalId() + " — " + r.failureReason());
            }
        }
    }

    private NmsBridgeHandle doResolve(NmsBridgeDescriptor descriptor) {
        try {
            List<String> classNames = descriptor.classNames();
            String memberName = descriptor.memberName();

            if (mappingsLoader != null && descriptor.mappingsKey().isPresent()) {
                MappingsLoader.MappingEntry entry = mappingsLoader.lookup(descriptor.mappingsKey().get());
                if (entry != null) {
                    if (entry.classNames() != null && !entry.classNames().isEmpty()) {
                        classNames = entry.classNames();
                    }
                    if (entry.memberName() != null && !entry.memberName().isEmpty()) {
                        memberName = entry.memberName();
                    }
                }
            }

            List<Class<?>> resolvedClasses = resolveClasses(classNames);
            if (resolvedClasses.isEmpty()) {
                return unavailable(descriptor, "None of the class names resolved: " + classNames);
            }

            NmsBridgeHandle lastFailure = null;
            for (Class<?> resolved : resolvedClasses) {
                NmsBridgeHandle handle = switch (descriptor.kind()) {
                    case METHOD -> resolveMethod(descriptor, resolved, memberName, descriptor.parameterTypeNames(), false);
                    case STATIC_METHOD -> resolveMethod(descriptor, resolved, memberName, descriptor.parameterTypeNames(), true);
                    case FIELD -> resolveField(descriptor, resolved, memberName);
                    case STATIC_FIELD -> resolveField(descriptor, resolved, memberName);
                    case CONSTRUCTOR -> resolveConstructor(descriptor, resolved, descriptor.parameterTypeNames());
                };
                if (handle.available()) {
                    return handle;
                }
                lastFailure = handle;
            }
            return lastFailure;
        } catch (Throwable t) {
            return unavailable(descriptor, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private NmsBridgeHandle resolveMethod(NmsBridgeDescriptor descriptor, Class<?> clazz,
            String memberName, List<List<String>> paramTypeNames, boolean isStatic) {
        Throwable lastError = null;
        List<List<String>> candidates = (paramTypeNames == null || paramTypeNames.isEmpty())
                ? List.of(List.of()) : paramTypeNames;
        for (List<String> candidate : candidates) {
            try {
                Class<?>[] paramTypes = resolveTypes(candidate);
                if (paramTypes == null) {
                    continue;
                }
                Method method = clazz.getDeclaredMethod(memberName, paramTypes);
                method.setAccessible(true);
                MethodHandles.Lookup methodLookup;
                try {
                    methodLookup = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
                } catch (IllegalAccessException ignored) {
                    methodLookup = MethodHandles.lookup();
                }
                MethodHandle handle = methodLookup.unreflect(method);
                String summary = clazz.getSimpleName() + "." + method.getName()
                        + "(" + String.join(",", candidate) + ")->" + method.getReturnType().getSimpleName();
                BridgeResolution resolution = new BridgeResolution(
                        descriptor.logicalId(), descriptor.kind(), true,
                        clazz.getName(), method.getName(), summary, "");
                return new NmsBridgeHandle(resolution, handle);
            } catch (Throwable t) {
                lastError = t;
            }
        }
        String reason = lastError != null
                ? lastError.getClass().getSimpleName() + ": " + lastError.getMessage()
                : "no matching overload found";
        return unavailable(descriptor, "Method '" + memberName + "' not resolved on " + clazz.getName() + ": " + reason);
    }

    private NmsBridgeHandle resolveField(NmsBridgeDescriptor descriptor, Class<?> clazz, String memberName) {
        Class<?> search = clazz;
        while (search != null) {
            try {
                Field field = search.getDeclaredField(memberName);
                field.setAccessible(true);
                MethodHandles.Lookup fieldLookup;
                try {
                    fieldLookup = MethodHandles.privateLookupIn(search, MethodHandles.lookup());
                } catch (IllegalAccessException ignored) {
                    fieldLookup = MethodHandles.lookup();
                }
                VarHandle handle = fieldLookup.unreflectVarHandle(field);
                String summary = clazz.getSimpleName() + "." + field.getName()
                        + ":" + field.getType().getSimpleName();
                BridgeResolution resolution = new BridgeResolution(
                        descriptor.logicalId(), descriptor.kind(), true,
                        clazz.getName(), field.getName(), summary, "");
                return new NmsBridgeHandle(resolution, handle);
            } catch (NoSuchFieldException ignored) {
                search = search.getSuperclass();
            } catch (Throwable t) {
                return unavailable(descriptor, "Field '" + memberName + "' inaccessible on " + clazz.getName() + ": " + t.getMessage());
            }
        }
        return unavailable(descriptor, "Field '" + memberName + "' not found on " + clazz.getName() + " or its superclasses");
    }

    private NmsBridgeHandle resolveConstructor(NmsBridgeDescriptor descriptor, Class<?> clazz,
            List<List<String>> paramTypeNames) {
        Throwable lastError = null;
        List<List<String>> candidates = (paramTypeNames == null || paramTypeNames.isEmpty())
                ? List.of(List.of()) : paramTypeNames;
        for (List<String> candidate : candidates) {
            try {
                Class<?>[] paramTypes = resolveTypes(candidate);
                if (paramTypes == null) {
                    continue;
                }
                Constructor<?> ctor = clazz.getDeclaredConstructor(paramTypes);
                ctor.setAccessible(true);
                MethodHandle handle = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup()).unreflectConstructor(ctor);
                String summary = clazz.getSimpleName() + ".<init>(" + String.join(",", candidate) + ")";
                BridgeResolution resolution = new BridgeResolution(
                        descriptor.logicalId(), descriptor.kind(), true,
                        clazz.getName(), "<init>", summary, "");
                return new NmsBridgeHandle(resolution, handle);
            } catch (Throwable t) {
                lastError = t;
            }
        }
        String reason = lastError != null ? lastError.getMessage() : "no matching constructor found";
        return unavailable(descriptor, "Constructor not resolved on " + clazz.getName() + ": " + reason);
    }

    private List<Class<?>> resolveClasses(List<String> names) {
        List<Class<?>> resolved = new java.util.ArrayList<>();
        for (String name : names) {
            try {
                resolved.add(Class.forName(name));
            } catch (ClassNotFoundException ignored) {
            }
        }
        return resolved;
    }

    private Class<?>[] resolveTypes(List<String> typeNames) {
        if (typeNames == null || typeNames.isEmpty()) {
            return new Class<?>[0];
        }
        Class<?>[] types = new Class<?>[typeNames.size()];
        for (int i = 0; i < typeNames.size(); i++) {
            types[i] = resolveType(typeNames.get(i));
            if (types[i] == null) {
                return null;
            }
        }
        return types;
    }

    private Class<?> resolveType(String typeName) {
        return switch (typeName) {
            case "int" -> int.class;
            case "long" -> long.class;
            case "boolean" -> boolean.class;
            case "double" -> double.class;
            case "float" -> float.class;
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "char" -> char.class;
            case "void" -> void.class;
            default -> {
                try {
                    yield Class.forName(typeName);
                } catch (ClassNotFoundException e) {
                    yield null;
                }
            }
        };
    }

    private NmsBridgeHandle unavailable(NmsBridgeDescriptor descriptor, String reason) {
        BridgeResolution resolution = new BridgeResolution(
                descriptor.logicalId(), descriptor.kind(), false,
                "", "", "resolution=unresolved", reason);
        return new NmsBridgeHandle(resolution);
    }
}
