package com.volmit.react.util;

@SuppressWarnings("hiding")
public class Cluster<T> implements ICluster<T>
{
	private ClusterType type;
	private T t;

	protected Cluster(ClusterType type, T t)
	{
		this.type = type;
		set(t);
	}

	@Override
	public ClusterType getType()
	{
		return type;
	}

	@Override
	public void set(T t)
	{
		this.t = t;
	}

	@Override
	public T get()
	{
		return t;
	}
}
