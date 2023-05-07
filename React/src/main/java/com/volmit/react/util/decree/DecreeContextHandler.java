/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.react.util.decree;


import com.volmit.react.React;
import com.volmit.react.util.collection.KMap;
import com.volmit.react.util.plugin.VolmitSender;

public interface DecreeContextHandler<T> {
    KMap<Class<?>, DecreeContextHandler<?>> contextHandlers = buildContextHandlers();

    static KMap<Class<?>, DecreeContextHandler<?>> buildContextHandlers() {
        KMap<Class<?>, DecreeContextHandler<?>> contextHandlers = new KMap<>();

        try {
            React.initialize("com.volmit.react.util.decree.context").forEach((i)
                    -> contextHandlers.put(((DecreeContextHandler<?>) i).getType(), (DecreeContextHandler<?>) i));
        } catch (Throwable e) {
            e.printStackTrace();
            e.printStackTrace();
        }

        return contextHandlers;
    }

    Class<T> getType();

    T handle(VolmitSender sender);
}
