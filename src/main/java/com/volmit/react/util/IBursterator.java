package com.volmit.react.util;

import java.util.List;

@SuppressWarnings("hiding")
public interface IBursterator<T>
{
	public void burst(T t);

	public void setTimeLock(double ms);

	public double getEstimatedTimeUse();

	public int flush();

	public int flush(Profiler p);

	public void queue(T t);

	public void queue(List<T> t);

	public void queue(T[] t);
}
