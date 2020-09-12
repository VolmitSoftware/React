package com.volmit.react.util;

public interface ICluster<T>
{
	public ClusterType getType();

	public void set(T t);

	public T get();
}
