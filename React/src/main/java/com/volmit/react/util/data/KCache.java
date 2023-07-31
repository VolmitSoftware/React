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

package com.volmit.react.util.data;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.volmit.react.util.math.RollingSequence;

public class KCache<K, V> {
    private final long max;
    private final LoadingCache<K, V> cache;
    private final boolean fastDump;
    private final RollingSequence msu = new RollingSequence(100);
    private CacheLoader<K, V> loader;

    public KCache(CacheLoader<K, V> loader, long max) {
        this(loader, max, false);
    }

    public KCache(CacheLoader<K, V> loader, long max, boolean fastDump) {
        this.max = max;
        this.fastDump = fastDump;
        this.loader = loader;
        this.cache = create(loader);
    }

    private LoadingCache<K, V> create(CacheLoader<K, V> loader) {
        return Caffeine
                .newBuilder()
                .maximumSize(max)
                .initialCapacity((int) (max))
                .build((k) -> loader == null ? null : loader.load(k));
    }


    public void setLoader(CacheLoader<K, V> loader) {
        this.loader = loader;
    }

    public void invalidate(K k) {
        cache.invalidate(k);
    }

    public void invalidate() {
        cache.invalidateAll();
    }

    public V get(K k) {
        return cache.get(k);
    }

    public long getSize() {
        return cache.estimatedSize();
    }

    public KCache<?, ?> getRawCache() {
        return this;
    }

    public long getMaxSize() {
        return max;
    }

    public boolean isClosed() {
        return false;
    }

    public boolean contains(K next) {
        return cache.getIfPresent(next) != null;
    }
}
