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

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.bukkit.Sound;

import java.io.IOException;
import java.lang.reflect.Method;

public class SoundTypeAdapter extends TypeAdapter<Sound> {
    private static final boolean IS_LEGACY;

    static {
        boolean legacy = false;
        try {
            // Test if Sound is an enum (1.19 and under) or interface (1.20+)
            Sound.class.isEnum();
            legacy = Sound.class.isEnum();
        } catch (Exception e) {
        }
        IS_LEGACY = legacy;
    }

    @Override
    public void write(JsonWriter out, Sound value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        if (IS_LEGACY) {
            // Use reflection to call name() method on enum
            try {
                Method nameMethod = value.getClass().getMethod("name");
                out.value((String) nameMethod.invoke(value));
            } catch (Exception e) {
                out.value(value.toString());
            }
        } else {
            try {
                Method keyMethod = value.getClass().getMethod("key");
                Object key = keyMethod.invoke(value);
                Method asStringMethod = key.getClass().getMethod("asString");
                out.value((String) asStringMethod.invoke(key));
            } catch (Exception e) {
                // Fallback to toString
                out.value(value.toString());
            }
        }
    }

    @Override
    public Sound read(JsonReader in) throws IOException {
        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        String soundKey;

        // Handle both string and object formats for backwards compatibility. This is
        // only neccessary if the consumer already had configurations from an older
        // version
        if (in.peek() == com.google.gson.stream.JsonToken.BEGIN_OBJECT) {
            in.beginObject();
            while (in.hasNext()) {
                in.nextName();
                in.skipValue();
            }
            in.endObject();
            return null;
        } else {
            // String format
            soundKey = in.nextString();
        }

        if (IS_LEGACY) {
            // For enum versions (1.19 and under)
            try {
                return Sound.valueOf(soundKey);
            } catch (IllegalArgumentException e) {
                return null;
            }
        } else {
            // For interface versions (1.20+)
            try {
                Class<?> registryClass = Class.forName("org.bukkit.Registry");
                Object soundsRegistry = registryClass.getField("SOUNDS").get(null);

                Class<?> namespacedKeyClass = Class.forName("org.bukkit.NamespacedKey");
                Method fromStringMethod = namespacedKeyClass.getMethod("fromString", String.class);

                // Clean up the sound key
                String cleanKey = soundKey;
                if (!cleanKey.contains(":")) {
                    cleanKey = "minecraft:" + cleanKey.toLowerCase();
                }

                Object namespacedKey = fromStringMethod.invoke(null, cleanKey);
                Method getMethod = soundsRegistry.getClass().getMethod("get", namespacedKeyClass);
                return (Sound) getMethod.invoke(soundsRegistry, namespacedKey);
            } catch (Exception e) {
                return null;
            }
        }
    }
}