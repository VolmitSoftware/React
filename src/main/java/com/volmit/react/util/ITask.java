package com.volmit.react.util;

public interface ITask
{
	int getId();

	void run();

	boolean isRepeating();

	String getName();

	double getComputeTime();

	double getTotalComputeTime();

	double getActiveTime();

	boolean hasCompleted();
}
