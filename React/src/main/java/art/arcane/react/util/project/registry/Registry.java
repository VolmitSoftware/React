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

import art.arcane.react.React;
import art.arcane.volmlib.util.io.JarScanner;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Registry<T extends Registered> {
  private final Map<String, T> idRegistry;
  private final Map<Class<?>, T> classRegistry;

  public Registry(Class<?> type, String packageName) {
    idRegistry = new ConcurrentHashMap<>();
    classRegistry = new ConcurrentHashMap<>();
    React.instance.getStartupTasks().add(() -> {
      React.verbose("Registering " + type.getSimpleName() + "s" + " in " + packageName);
      String p = React.instance.jar().getAbsolutePath();
      p = p.replaceAll("\\Q.jar.jar\\E", ".jar");
      JarScanner j = new JarScanner(new File(p), packageName);
      try {
        j.scan();
        j.getClasses().stream()
            .filter(type::isAssignableFrom)
            .sorted(Comparator.comparing(Class::getName))
            .forEach(candidate -> registerCandidate(type, candidate));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  public T register(T instance) {
    if (instance == null) {
      return null;
    }

    String id = instance.getId();
    if (id == null || id.isBlank()) {
      return null;
    }

    if (!instance.autoRegister()) {
      React.verbose("Skipped " + instance.getConfigCategory() + " " + id + " due to autoRegister=false");
      return null;
    }

    T existing = idRegistry.get(id);
    if (existing != null) {
      React.warn("Duplicate " + instance.getClass().getSimpleName() + " id " + id + " rejected. Keeping the first registration.");
      return existing;
    }

    instance.loadConfiguration();
    idRegistry.put(id, instance);
    classRegistry.put(instance.getClass(), instance);
    React.verbose("Register " + instance.getConfigCategory() + " " + id + " (" + instance.getClass().getSimpleName() + ")");
    return instance;
  }

  public T unregister(String id) {
    if (id == null || id.isBlank()) {
      return null;
    }

    T removed = idRegistry.remove(id);
    if (removed != null) {
      classRegistry.remove(removed.getClass(), removed);
      React.verbose("Unregister " + removed.getConfigCategory() + " " + id + " (" + removed.getClass().getSimpleName() + ")");
    }
    return removed;
  }

  private static boolean hasNoArgConstructor(Class<?> clazz) {
    try {
      Constructor<?> constructor = clazz.getConstructor();
      return Modifier.isPublic(constructor.getModifiers());
    } catch (Throwable ignored) {
      return false;
    }
  }

  private void registerCandidate(Class<?> type, Class<?> candidate) {
    if (candidate.isInterface() || Modifier.isAbstract(candidate.getModifiers())) {
      return;
    }

    if (!hasNoArgConstructor(candidate)) {
      React.warn("Skipped " + type.getSimpleName() + " " + candidate.getName() + " because it has no public no-arg constructor.");
      return;
    }

    T instance;
    try {
      instance = (T) candidate.getConstructor().newInstance();
    } catch (Throwable e) {
      React.warn("Failed to instantiate " + type.getSimpleName() + " " + candidate.getName() + ": " + e.getMessage());
      return;
    }

    if (instance == null) {
      return;
    }

    if (!instance.autoRegister()) {
      React.verbose("Skipped " + instance.getConfigCategory() + " " + instance.getId() + " (" + candidate.getSimpleName() + ") due to autoRegister=false");
      return;
    }

    instance.loadConfiguration();
    if (idRegistry.containsKey(instance.getId())) {
      React.warn("Duplicate " + type.getSimpleName() + " id " + instance.getId() + " found in " + candidate.getName() + ". Keeping the first registration.");
      return;
    }

    idRegistry.put(instance.getId(), instance);
    classRegistry.put(instance.getClass(), instance);
    React.verbose("Register " + instance.getConfigCategory() + " " + instance.getId() + " (" + candidate.getSimpleName() + ")");
  }

  public Set<Class<?>> classes() {
    return classRegistry.keySet();
  }

  public Set<String> ids() {
    return idRegistry.keySet();
  }

  public int size() {
    return idRegistry.size();
  }

  public Collection<T> all() {
    return idRegistry.values();
  }

  public <X extends T> X get(String id) {
    return (X) idRegistry.get(id);
  }

  public <X extends T> X get(Class<X> clazz) {
    return (X) classRegistry.get(clazz);
  }

  public <X extends T> X get() {
    for (T i : classRegistry.values()) {
      try {
        return (X) i;
      } catch (Throwable ignored) {

      }
    }

    return null;
  }
}
