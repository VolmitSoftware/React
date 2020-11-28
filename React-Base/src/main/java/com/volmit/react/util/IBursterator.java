package com.volmit.react.util;

import java.util.List;

@SuppressWarnings("hiding")
public interface IBursterator<T>
{
	void burst(T t);

	void setTimeLock(double ms);

	double getEstimatedTimeUse();

	int flush();

	int flush(Profiler p);

	void queue(T t);

	void queue(List<T> t);

	void queue(T[] t);
}
