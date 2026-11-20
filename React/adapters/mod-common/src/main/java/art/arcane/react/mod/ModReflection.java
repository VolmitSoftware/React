package art.arcane.react.mod;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calls into other mods' public classes without compiling against them. Every lookup failure surfaces as a
 * {@link ReflectiveOperationException} so a caller can mark that integration unavailable.
 */
final class ModReflection {
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();

    private ModReflection() {
    }

    /** The class from React's loader, the server's loader or the thread context loader; null when absent. */
    static Class<?> find(String name) {
        for (ClassLoader loader : new ClassLoader[]{ModReflection.class.getClassLoader(),
                net.minecraft.server.MinecraftServer.class.getClassLoader(), Thread.currentThread().getContextClassLoader()}) {
            if (loader == null) {
                continue;
            }
            try {
                return Class.forName(name, true, loader);
            } catch (ClassNotFoundException | LinkageError ignored) {
                // Try the next loader.
            }
        }
        return null;
    }

    static Class<?> require(String name) throws ClassNotFoundException {
        Class<?> found = find(name);
        if (found == null) {
            throw new ClassNotFoundException(name);
        }
        return found;
    }

    static Object callStatic(Class<?> owner, String name, Object... args) throws ReflectiveOperationException {
        return method(owner, name, args.length, true).invoke(null, args);
    }

    static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        if (target == null) {
            throw new NoSuchMethodException(name + " on null");
        }
        return method(target.getClass(), name, args.length, false).invoke(target, args);
    }

    static double number(Object target, String name) throws ReflectiveOperationException {
        Object value = call(target, name);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Boolean flag) {
            return flag ? 1D : 0D;
        }
        throw new NoSuchMethodException(name + " is not numeric");
    }

    private static Method method(Class<?> owner, String name, int arity, boolean isStatic) throws NoSuchMethodException {
        String key = owner.getName() + '#' + name + '/' + arity;
        Method cached = METHODS.get(key);
        if (cached != null) {
            return cached;
        }
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Method candidate : type.getMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterCount() == arity
                        && Modifier.isStatic(candidate.getModifiers()) == isStatic) {
                    candidate.trySetAccessible();
                    METHODS.put(key, candidate);
                    return candidate;
                }
            }
        }
        throw new NoSuchMethodException(key);
    }
}
