package com.volmit.react.api;

import primal.lang.collection.GList;
import primal.lang.collection.GSet;

public interface ICache<K, V> {
    GSet<V> get(K k);

    void put(K k, V v);

    void clear(K k);

    void clear();

    boolean has(K k);

    GList<K> k();
}
