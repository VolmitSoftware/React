/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.react.util;

import com.google.gson.Gson;
import com.volmit.react.React;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class PersistentJson {
    private static final Gson gson = new Gson();

    public static void write(PersistentDataContainer c, String key, Object data) {
        c.set(new NamespacedKey(React.instance, key), PersistentDataType.STRING, gson.toJson(data));
    }

    private static <T> T fromJSON(PersistentDataContainer c, String key, Class<T> type) {
        String s = c.get(new NamespacedKey(React.instance, key), PersistentDataType.STRING);

        if(s == null) {
            return gson.fromJson(s, type);
        }

        return null;
    }
}
