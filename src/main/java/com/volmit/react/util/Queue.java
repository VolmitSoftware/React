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

import java.util.Arrays;
import java.util.List;

public interface Queue<T> {
    static <T> Queue<T> create(List<T> t) {
        return new ShurikenQueue<T>().queue(t);
    }

    @SuppressWarnings("unchecked")
    static <T> Queue<T> create(T... t) {
        return new ShurikenQueue<T>().queue(Arrays.stream(t).toList());
    }

    Queue<T> queue(T t);

    Queue<T> queue(List<T> t);

    boolean hasNext(int amt);

    boolean hasNext();

    T next();

    List<T> next(int amt);

    Queue<T> clear();

    int size();

    boolean contains(T p);
}
