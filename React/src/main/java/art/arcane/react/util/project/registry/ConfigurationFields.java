/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.util.project.registry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

final class ConfigurationFields {
  private ConfigurationFields() {
  }

  static void copy(Object source, Object target) {
    for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
      for (Field field : type.getDeclaredFields()) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || Modifier.isTransient(modifiers)) {
          continue;
        }
        field.setAccessible(true);
        try {
          Object value = field.get(source);
          if (value != null) {
            field.set(target, value);
          }
        } catch (IllegalAccessException e) {
          throw new IllegalStateException("Cannot copy config field " + type.getName() + "." + field.getName(), e);
        }
      }
    }
  }
}
