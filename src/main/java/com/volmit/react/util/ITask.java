package com.volmit.react.util;

public interface ITask
{
	public int getId();

	public void run();

	public boolean isRepeating();

	public String getName();

	public double getComputeTime();

	public double getTotalComputeTime();

	public double getActiveTime();

	public boolean hasCompleted();
}
