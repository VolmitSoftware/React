package art.arcane.react.util.project.registry;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Changes every config field it understands on an object, so a copy that silently skips a field
 * shows up as a value that never arrived.
 */
final class ConfigFieldMutator {
  private static final int MAX_DEPTH = 3;

  private ConfigFieldMutator() {
  }

  record Report(List<Field> changed, List<Field> unsupported) {
    Report() {
      this(new ArrayList<>(), new ArrayList<>());
    }
  }

  static Report mutate(Object instance, Random random, boolean allowNulls) {
    Report report = new Report();
    for (Field field : ConfigurationFieldScenarios.hierarchyFields(instance.getClass())) {
      if (!ConfigurationFieldScenarios.copyable(field)) {
        continue;
      }
      try {
        Object current = field.get(instance);
        if (allowNulls && !field.getType().isPrimitive() && random.nextInt(5) == 0) {
          field.set(instance, null);
          report.changed().add(field);
          continue;
        }
        Object next = changed(field.getGenericType(), field.getType(), current, random, 0);
        if (next == null) {
          report.unsupported().add(field);
          continue;
        }
        field.set(instance, next);
        report.changed().add(field);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
    }
    return report;
  }

  static boolean isScalar(Class<?> type) {
    return type.isPrimitive() || type == Boolean.class || type == Byte.class || type == Short.class
        || type == Character.class || type == Integer.class || type == Long.class || type == Float.class
        || type == Double.class || type == String.class || type.isEnum();
  }

  private static Object changed(Type genericType, Class<?> type, Object current, Random random, int depth) {
    if (isScalar(type)) {
      return scalar(type, current, random);
    }
    if (type.isArray()) {
      return array(type.getComponentType(), current, random);
    }
    if (type == List.class || type == Collection.class) {
      List<Object> values = current == null ? new ArrayList<>() : new ArrayList<>((Collection<?>) current);
      addElement(values, typeArgument(genericType, 0), random);
      return values;
    }
    if (type == Set.class) {
      Set<Object> values = current == null ? new LinkedHashSet<>() : new LinkedHashSet<>((Collection<?>) current);
      addElement(values, typeArgument(genericType, 0), random);
      return values;
    }
    if (type == Map.class) {
      Map<Object, Object> values = current == null ? new LinkedHashMap<>() : new LinkedHashMap<>((Map<?, ?>) current);
      Class<?> keyType = typeArgument(genericType, 0);
      Class<?> valueType = typeArgument(genericType, 1);
      if (keyType == String.class && valueType != null && isScalar(valueType)) {
        values.put("mutated" + random.nextInt(1_000_000), scalar(valueType, null, random));
      }
      return values;
    }
    if (isConfigObject(type) && depth < MAX_DEPTH) {
      Object nested = current == null ? instantiate(type) : current;
      if (nested == null) {
        return null;
      }
      Object replacement = current == null ? nested : shallowCopy(nested);
      if (replacement == null) {
        return null;
      }
      for (Field field : ConfigurationFieldScenarios.hierarchyFields(type)) {
        if (!ConfigurationFieldScenarios.copyable(field)) {
          continue;
        }
        try {
          Object next = changed(field.getGenericType(), field.getType(), field.get(replacement), random, depth + 1);
          if (next != null) {
            field.set(replacement, next);
          }
        } catch (IllegalAccessException e) {
          throw new IllegalStateException(e);
        }
      }
      return replacement;
    }
    return null;
  }

  private static boolean isConfigObject(Class<?> type) {
    return type.getName().startsWith("art.arcane.react.") && !type.isInterface() && !type.isEnum()
        && !type.isRecord() && !Registered.class.isAssignableFrom(type);
  }

  private static void addElement(Collection<Object> values, Class<?> elementType, Random random) {
    if (elementType != null && isScalar(elementType)) {
      values.add(scalar(elementType, null, random));
    }
  }

  private static Class<?> typeArgument(Type genericType, int index) {
    if (genericType instanceof ParameterizedType parameterized
        && parameterized.getActualTypeArguments().length > index
        && parameterized.getActualTypeArguments()[index] instanceof Class<?> argument) {
      return argument;
    }
    return null;
  }

  private static Object array(Class<?> componentType, Object current, Random random) {
    if (!isScalar(componentType)) {
      return null;
    }
    int length = current == null ? 0 : Array.getLength(current);
    Object next = Array.newInstance(componentType, length + 1);
    for (int i = 0; i < length; i++) {
      Array.set(next, i, Array.get(current, i));
    }
    Array.set(next, length, scalar(componentType, null, random));
    return next;
  }

  private static Object scalar(Class<?> type, Object current, Random random) {
    int step = 1 + random.nextInt(9);
    if (type == boolean.class || type == Boolean.class) {
      return current == null ? Boolean.TRUE : (Object) !((Boolean) current);
    }
    if (type == byte.class || type == Byte.class) {
      return (byte) ((current == null ? 0 : (Byte) current) + step);
    }
    if (type == short.class || type == Short.class) {
      return (short) ((current == null ? 0 : (Short) current) + step);
    }
    if (type == char.class || type == Character.class) {
      char base = current == null ? 'a' : (Character) current;
      return (char) ('a' + ((base - 'a' + step) & 0xF));
    }
    if (type == int.class || type == Integer.class) {
      return (current == null ? 0 : (Integer) current) + step;
    }
    if (type == long.class || type == Long.class) {
      return (current == null ? 0L : (Long) current) + step;
    }
    if (type == float.class || type == Float.class) {
      return (current == null ? 0F : (Float) current) + step + 0.5F;
    }
    if (type == double.class || type == Double.class) {
      return (current == null ? 0D : (Double) current) + step + 0.5D;
    }
    if (type == String.class) {
      return (current == null ? "" : current) + "mutated" + random.nextInt(1_000_000);
    }
    if (type.isEnum()) {
      Object[] constants = type.getEnumConstants();
      if (constants.length < 2) {
        return null;
      }
      int ordinal = current == null ? 0 : ((Enum<?>) current).ordinal();
      return constants[(ordinal + 1 + random.nextInt(constants.length - 1)) % constants.length];
    }
    return null;
  }

  private static Object instantiate(Class<?> type) {
    try {
      Constructor<?> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null;
    }
  }

  private static Object shallowCopy(Object original) {
    Object copy = instantiate(original.getClass());
    if (copy == null) {
      return null;
    }
    for (Field field : ConfigurationFieldScenarios.hierarchyFields(original.getClass())) {
      if (!ConfigurationFieldScenarios.copyable(field)) {
        continue;
      }
      try {
        field.set(copy, field.get(original));
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
    }
    return copy;
  }
}
