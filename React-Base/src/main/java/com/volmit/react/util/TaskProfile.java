package com.volmit.react.util;

public class TaskProfile
{
	private double activeTime;
	private double computeTickTime;
	private double computeTime;

	public TaskProfile()
	{
		activeTime = 0;
		computeTickTime = 0;
		computeTickTime = 0;
	}

	public double getActiveTime()
	{
		return activeTime;
	}

	public void setActiveTime(double activeTime)
	{
		this.activeTime = activeTime;
	}

	public double getComputeTickTime()
	{
		return computeTickTime;
	}

	public void setComputeTickTime(double computeTickTime)
	{
		this.computeTickTime = computeTickTime;
	}

	public double getComputeTime()
	{
		return computeTime;
	}

	public void setComputeTime(double computeTime)
	{
		this.computeTime = computeTime;
	}
}
