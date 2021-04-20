package com.volmit.react.util;

@SuppressWarnings("hiding")
public interface ICluster<T> {
    ClusterType getType();

    void set(T t);

    T get();
}
