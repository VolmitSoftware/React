package primal.compute.math;

import primal.logic.format.F;

public class Profiler
{
	private long nanos;
	private long startNano;
	private long millis;
	private long startMillis;
	private double time;
	private boolean profiling;

	public static Profiler start()
	{
		Profiler p = new Profiler();
		p.begin();

		return p;
	}

	public Profiler()
	{
		reset();
		profiling = false;
	}

	public void begin()
	{
		profiling = true;
		startNano = System.nanoTime();
		startMillis = System.currentTimeMillis();
	}

	public void end()
	{
		if(!profiling)
		{
			return;
		}

		profiling = false;
		nanos = System.nanoTime() - startNano;
		millis = System.currentTimeMillis() - startMillis;
		time = (double) nanos / 1000000.0;
		time = (double) millis - time > 1.01 ? millis : time;
	}

	public void reset()
	{
		nanos = -1;
		millis = -1;
		startNano = -1;
		startMillis = -1;
		time = -0;
		profiling = false;
	}

	public String getTime(int dec)
	{
		if(getNanoseconds() < 1000.0)
		{
			return F.f(getNanoseconds()) + "ns";
		}

		if(getMilliseconds() < 1000.0)
		{
			return F.f(getMilliseconds(), dec) + "ms";
		}

		if(getSeconds() < 60.0)
		{
			return F.f(getSeconds(), dec) + "s";
		}

		if(getMinutes() < 60.0)
		{
			return F.f(getMinutes(), dec) + "m";
		}

		return F.f(getHours(), dec) + "h";
	}

	public double getTicks()
	{
		return getMilliseconds() / 50.0;
	}

	public double getSeconds()
	{
		return getMilliseconds() / 1000.0;
	}

	public double getMinutes()
	{
		return getSeconds() / 60.0;
	}

	public double getHours()
	{
		return getMinutes() / 60.0;
	}

	public double getMilliseconds()
	{
		nanos = System.nanoTime() - startNano;
		millis = System.currentTimeMillis() - startMillis;
		time = (double) nanos / 1000000.0;
		time = (double) millis - time > 1.01 ? millis : time;
		return time;
	}

	public long getNanoseconds()
	{
		return (long) (time * 1000000.0);
	}

	public long getNanos()
	{
		return nanos;
	}

	public long getStartNano()
	{
		return startNano;
	}

	public long getMillis()
	{
		return millis;
	}

	public long getStartMillis()
	{
		return startMillis;
	}

	public double getTime()
	{
		return time;
	}

	public boolean isProfiling()
	{
		return profiling;
	}
}
