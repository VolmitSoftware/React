package com.volmit.react.util.cache;


import com.volmit.react.React;
import com.volmit.react.util.function.NastySupplier;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class AtomicCache<T> {
    private transient final AtomicReference<T> t;
    private transient final AtomicBoolean set;
    private transient final ReentrantLock lock;
    private transient final boolean nullSupport;

    public AtomicCache() {
        this(false);
    }

    public AtomicCache(boolean nullSupport) {
        set = nullSupport ? new AtomicBoolean() : null;
        t = new AtomicReference<>();
        lock = new ReentrantLock();
        this.nullSupport = nullSupport;
    }

    public void reset() {
        t.set(null);

        if (nullSupport) {
            set.set(false);
        }
    }

    public T aquireNasty(NastySupplier<T> t) {
        return aquire(() -> {
            try {
                return t.get();
            } catch (Throwable e) {
                return null;
            }
        });
    }

    public T aquireNastyPrint(NastySupplier<T> t) {
        return aquire(() -> {
            try {
                return t.get();
            } catch (Throwable e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    public T aquire(Supplier<T> t) {
        if (this.t.get() != null) {
            return this.t.get();
        } else if (nullSupport && set.get()) {
            return null;
        }

        lock.lock();

        if (this.t.get() != null) {
            lock.unlock();
            return this.t.get();
        } else if (nullSupport && set.get()) {
            lock.unlock();
            return null;
        }

        try {
            this.t.set(t.get());

            if (nullSupport) {
                set.set(true);
            }
        } catch (Throwable e) {
            React.error("Atomic cache failure!");
            e.printStackTrace();
        }

        lock.unlock();

        return this.t.get();
    }
}
