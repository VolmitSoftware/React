package art.arcane.react.util.project.registry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Plain-reflection restatement of the Curse 23.4.3 calls the old Registered.copyConfigurationFields
 * made: walk the target's fields, then find the source field by name starting at the source's own
 * class. CurseRecordedBehavior holds what the real library did, and the tests check this class
 * against that recording before using it as the reference for anything else.
 */
final class LegacyCurseCopy {
  private LegacyCurseCopy() {
  }

  static void copy(Object loadedObject, Object self) {
    Class<?> loadedType = loadedObject.getClass();
    List<Field> fields = new ArrayList<>();
    for (Class<?> type = self.getClass(); type != Object.class; type = type.getSuperclass()) {
      for (Field field : type.getDeclaredFields()) {
        fields.add(field);
      }
    }

    for (Field field : fields) {
      int modifiers = field.getModifiers();
      if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || Modifier.isTransient(modifiers)) {
        continue;
      }

      Field from = findByName(loadedType, field.getName());
      if (from == null) {
        throw new NoSuchElementException("No value present");
      }

      Object value;
      try {
        from.setAccessible(true);
        value = from.get(Modifier.isStatic(from.getModifiers()) ? null : loadedObject);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }

      if (value != null) {
        try {
          field.setAccessible(true);
          field.set(self, value);
        } catch (Throwable e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  private static Field findByName(Class<?> type, String name) {
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      try {
        return current.getDeclaredField(name);
      } catch (NoSuchFieldException | SecurityException ignored) {
      }
    }
    return null;
  }
}
