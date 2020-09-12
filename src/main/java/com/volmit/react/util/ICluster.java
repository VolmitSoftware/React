package com.volmit.react.util;

public interface ICluster<T>
{
	ClusterType getType();

	void set(T t);

	T get();
}
