package art.arcane.react.mod.cost;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * The mod a ticket key names. Forge keys its forced-chunk tickets with an owner that carries a {@code modId} string and
 * NeoForge with one that carries an {@code id} resource location; any other key names no mod.
 */
public final class TicketKeyOwners {
    private static final ClassValue<Field> FIELDS = new ClassValue<>() {
        @Override
        protected Field computeValue(Class<?> type) {
            return ownerField(type);
        }
    };

    private TicketKeyOwners() {
    }

    /** The mod id or namespace {@code key} names, or null. */
    public static String namespaceOf(Object key) {
        if (key == null) {
            return null;
        }
        Field field = FIELDS.get(key.getClass());
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(key);
            if (value == null) {
                return null;
            }
            String text = value.toString();
            return field.getType() == String.class ? text : namespace(text);
        } catch (IllegalAccessException | RuntimeException failure) {
            return null;
        }
    }

    /** The namespace of {@code namespace:path}, or null when {@code id} has none. */
    public static String namespace(String id) {
        if (id == null) {
            return null;
        }
        int colon = id.indexOf(':');
        return colon <= 0 ? null : id.substring(0, colon);
    }

    private static Field ownerField(Class<?> type) {
        if (type.isPrimitive() || type.getName().startsWith("java.") || type.isEnum()) {
            return null;
        }
        Field field = declared(type, "modId");
        if (field == null || field.getType() != String.class) {
            field = declared(type, "id");
        }
        if (field == null || Modifier.isStatic(field.getModifiers())) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static Field declared(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException missing) {
            return null;
        }
    }
}
