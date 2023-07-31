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

package com.volmit.react.util.registry;

import com.volmit.react.React;
import com.volmit.react.util.io.JarScanner;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Registry<T extends Registered> {
    private Map<String, T> idRegistry;
    private Map<Class<?>, T> classRegistry;

    public Registry(Class<?> type, String packageName) {
        idRegistry = new HashMap<>();
        classRegistry = new HashMap<>();
        React.instance.getStartupTasks().add(() -> {
            React.verbose("Registering " + type.getSimpleName() + "s" + " in " + packageName);
            String p = React.instance.jar().getAbsolutePath();
            p = p.replaceAll("\\Q.jar.jar\\E", ".jar");
            JarScanner j = new JarScanner(new File(p), packageName);
            try {
                j.scan();
                j.getClasses().stream()
                        .filter(i -> i.isAssignableFrom(type) || type.isAssignableFrom(i))
                        .map((i) -> {
                            try {
                                return (T) i.getConstructor().newInstance();
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }

                            return null;
                        })
                        .forEach((i) -> {
                            if (i != null && i.autoRegister()) {
                                i.loadConfiguration();
                                idRegistry.put(i.getId(), i);
                                classRegistry.put(i.getClass(), i);
                                React.verbose("Register " + i.getConfigCategory() + " " + i.getId() + " (" + i.getClass().getSimpleName() + ")");
                            }
                        });

                this.idRegistry = Collections.unmodifiableMap(idRegistry);
                this.classRegistry = Collections.unmodifiableMap(classRegistry);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
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
