package com.volmit.react.controller;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.bukkit.plugin.Plugin;

import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;

public class MemoryController extends Controller
{
	private boolean running;
	private Method in;

	public boolean isRunning()
	{
		return running;
	}

	public Method getIn()
	{
		return in;
	}

	public long getMemoryUsagePlugin(Plugin p)
	{
		long v = 0;

		for(Field i : p.getClass().getDeclaredFields())
		{
			if(Modifier.isStatic(i.getModifiers()))
			{
				continue;
			}

			i.setAccessible(true);

			try
			{
				v += getMemoryUsage(i.get(p));
			}

			catch(IllegalArgumentException | IllegalAccessException e)
			{

			}
		}

		return v;
	}

	public long getMemoryUsage(Object o)
	{
		try
		{
			return running && in != null ? (long) in.invoke(null, o) : -1;
		}

		catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
		{

		}

		return -1;
	}

	@Override
	public void dump(JSONObject object)
	{

	}

	@Override
	public void start()
	{
		running = false;

		try
		{
			Class<?> c = Class.forName("com.javamex.classmexer.MemoryUtil");
			in = c.getDeclaredMethod("deepMemoryUsageOf", Object.class);
			System.out.println("React Memory Monitor LINKED!");
			running = true;
		}

		catch(Throwable e)
		{
			running = false;
		}
	}

	@Override
	public void stop()
	{

	}

	@Override
	public void tick()
	{

	}

	@Override
	public int getInterval()
	{
		return 948;
	}

	@Override
	public boolean isUrgent()
	{
		return false;
	}
}
