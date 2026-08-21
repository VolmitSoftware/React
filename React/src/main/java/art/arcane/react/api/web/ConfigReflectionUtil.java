package art.arcane.react.api.web;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

class ConfigReflectionUtil {

    private ConfigReflectionUtil() {}

    static Class<?> normalizeType(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == boolean.class) return Boolean.class;
        if (type == char.class) return Character.class;
        return type;
    }

    static String classifyType(Class<?> normalized) {
        if (normalized == null) {
            return null;
        }
        if (normalized == Boolean.class) {
            return "bool";
        }
        if (normalized == Integer.class || normalized == Long.class
                || normalized == Short.class || normalized == Byte.class) {
            return "int";
        }
        if (normalized == Double.class || normalized == Float.class) {
            return "double";
        }
        if (normalized == String.class || normalized == Character.class) {
            return "string";
        }
        if (normalized.isEnum()) {
            return "enum";
        }
        return null;
    }

    static String displayName(String key) {
        if (key == null || key.isBlank()) {
            return "Unnamed";
        }
        String spaced = key
            .replace('_', ' ')
            .replace('-', ' ')
            .replaceAll("([a-z])([A-Z])", "$1 $2")
            .trim();
        if (spaced.isBlank()) {
            return key;
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    static List<Field> collectFields(Class<?> type) {
        List<Field> out = new ArrayList<>();
        collectFieldsRecursive(type, out);
        return out;
    }

    private static void collectFieldsRecursive(Class<?> type, List<Field> out) {
        if (type == null || type == Object.class) {
            return;
        }
        collectFieldsRecursive(type.getSuperclass(), out);
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }
            out.add(field);
        }
    }
}
