package com.volmit.react.legacyutil;

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
