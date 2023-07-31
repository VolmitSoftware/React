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

package com.volmit.react.util.atomics;

import com.volmit.react.React;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class AsyncRequest<T> {
    private final Supplier<T> supplier;
    private final AtomicReference<T> value;
    private final AtomicBoolean active;

    public AsyncRequest(Supplier<T> supplier, T defaultValue) {
        this.supplier = supplier;
        this.active = new AtomicBoolean(false);
        this.value = new AtomicReference<T>(defaultValue);
    }

    public synchronized T request() {
        if (!active.get()) {
            active.set(true);
            React.burst.lazy(() -> {
                value.set(supplier.get());
                active.set(false);
            });
        }

        return value.get();
    }
}
