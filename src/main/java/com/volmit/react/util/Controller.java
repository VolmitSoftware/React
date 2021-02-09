package com.volmit.react.util;

import org.bukkit.event.Listener;

public abstract class Controller implements IController, Listener
{
	private double time;

	public Controller()
	{
		time = 0;
	}

	@Override
	public void setTime(double ms)
	{
		time = ms;
	}

	@Override
	public double getTime()
	{
		return time;
	}
}
