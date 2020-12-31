package primal.bukkit.sched;

import org.bukkit.Bukkit;

import primal.bukkit.plugin.PrimalPlugin;
import primal.lang.collection.FinalInteger;
import primal.lang.collection.GList;

/**
 * Job Scheduling
 *
 * @author cyberpwn
 *
 */
public class J
{
	private static GList<Runnable> afterStartup = new GList<>();
	private static GList<Runnable> afterStartupAsync = new GList<>();
	private static boolean started = false;

	/**
	 * Dont call this unless you know what you are doing!
	 */
	public static void executeAfterStartupQueue()
	{
		if(started)
		{
			return;
		}

		started = true;

		for(Runnable r : afterStartup)
		{
			s(r);
		}

		for(Runnable r : afterStartupAsync)
		{
			a(r);
		}

		afterStartup = null;
		afterStartupAsync = null;
	}

	/**
	 * Schedule a sync task to be run right after startup. If the server has already
	 * started ticking, it will simply run it in a sync task.
	 *
	 * If you dont know if you should queue this or not, do so, it's pretty
	 * forgiving.
	 *
	 * @param r
	 *            the runnable
	 */
	public static void ass(Runnable r)
	{
		if(started)
		{
			s(r);
		}

		else
		{
			afterStartup.add(r);
		}
	}

	/**
	 * Schedule an async task to be run right after startup. If the server has
	 * already started ticking, it will simply run it in an async task.
	 *
	 * If you dont know if you should queue this or not, do so, it's pretty
	 * forgiving.
	 *
	 * @param r
	 *            the runnable
	 */
	public static void asa(Runnable r)
	{
		if(started)
		{
			a(r);
		}

		else
		{
			afterStartupAsync.add(r);
		}
	}

	/**
	 * Queue a sync task
	 *
	 * @param r
	 *            the runnable
	 */
	public static void s(Runnable r)
	{
		Bukkit.getScheduler().scheduleSyncDelayedTask(PrimalPlugin.instance, r);
	}

	/**
	 * Queue a sync task
	 *
	 * @param r
	 *            the runnable
	 * @param delay
	 *            the delay to wait in ticks before running
	 */
	public static void s(Runnable r, int delay)
	{
		Bukkit.getScheduler().scheduleSyncDelayedTask(PrimalPlugin.instance, r, delay);
	}

	/**
	 * Cancel a sync repeating task
	 *
	 * @param id
	 *            the task id
	 */
	public static void csr(int id)
	{
		Bukkit.getScheduler().cancelTask(id);
	}

	/**
	 * Start a sync repeating task
	 *
	 * @param r
	 *            the runnable
	 * @param interval
	 *            the interval
	 * @return the task id
	 */
	public static int sr(Runnable r, int interval)
	{
		return Bukkit.getScheduler().scheduleSyncRepeatingTask(PrimalPlugin.instance, r, 0, interval);
	}

	/**
	 * Start a sync repeating task for a limited amount of ticks
	 *
	 * @param r
	 *            the runnable
	 * @param interval
	 *            the interval in ticks
	 * @param intervals
	 *            the maximum amount of intervals to run
	 */
	public static void sr(Runnable r, int interval, int intervals)
	{
		FinalInteger fi = new FinalInteger(0);

		new SR()
		{
			@Override
			public void run()
			{
				fi.add(1);
				r.run();

				if(fi.get() >= intervals)
				{
					cancel();
				}
			}
		};
	}

	/**
	 * Call an async task
	 *
	 * @param r
	 *            the runnable
	 */
	@SuppressWarnings("deprecation")
	public static void a(Runnable r)
	{
		Bukkit.getScheduler().scheduleAsyncDelayedTask(PrimalPlugin.instance, r, 0);
	}

	/**
	 * Call an async task dealyed
	 *
	 * @param r
	 *            the runnable
	 * @param delay
	 *            the delay to wait before running
	 */
	@SuppressWarnings("deprecation")
	public static void a(Runnable r, int delay)
	{
		Bukkit.getScheduler().scheduleAsyncDelayedTask(PrimalPlugin.instance, r, delay);
	}

	/**
	 * Cancel an async repeat task
	 *
	 * @param id
	 *            the id
	 */
	public static void car(int id)
	{
		Bukkit.getScheduler().cancelTask(id);
	}

	/**
	 * Start an async repeat task
	 *
	 * @param r
	 *            the runnable
	 * @param interval
	 *            the interval in ticks
	 * @return the task id
	 */
	@SuppressWarnings("deprecation")
	public static int ar(Runnable r, int interval)
	{
		return Bukkit.getScheduler().scheduleAsyncRepeatingTask(PrimalPlugin.instance, r, 0, interval);
	}

	/**
	 * Start an async repeating task for a limited time
	 *
	 * @param r
	 *            the runnable
	 * @param interval
	 *            the interval
	 * @param intervals
	 *            the intervals to run
	 */
	public static void ar(Runnable r, int interval, int intervals)
	{
		FinalInteger fi = new FinalInteger(0);

		new AR()
		{
			@Override
			public void run()
			{
				fi.add(1);
				r.run();

				if(fi.get() >= intervals)
				{
					cancel();
				}
			}
		};
	}
}
