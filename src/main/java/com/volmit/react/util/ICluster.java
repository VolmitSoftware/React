package com.volmit.react.util;

@SuppressWarnings("hiding")
public interface ICluster<T>
{
	public ClusterType getType();

	public void set(T t);

	public T get();
}
