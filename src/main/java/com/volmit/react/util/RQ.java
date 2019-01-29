package com.volmit.react.util;

import org.bukkit.Bukkit;

import com.volmit.volume.lang.collections.GList;

public class RQ extends Thread
{
	private static GList<A> r = null;

	public RQ()
	{
		r = new GList<A>();
		setPriority(Thread.MIN_PRIORITY);
		setName("React Reactor");
	}

	@Override
	public void run()
	{
		while(!interrupted())
		{
			while(!r.isEmpty())
			{
				try
				{
					r.pop().run();
				}

				catch(Throwable e)
				{

				}
			}

			r.clear();

			try
			{
				Thread.sleep(50);
			}

			catch(InterruptedException e)
			{
				return;
			}
		}
	}

	@SuppressWarnings("deprecation")
	public static void run(A a)
	{
		if(r == null || r.size() > 35)
		{
			try
			{
				Bukkit.getScheduler().scheduleAsyncDelayedTask(Bukkit.getPluginManager().getPlugin("React"), a);
			}
			
			catch(Throwable e)
			{
				// Hush
			}
		}

		else
		{
			r.add(a);
		}
	}
}
