package com.volmit.react.util;

import com.volmit.react.React;
import com.volmit.react.Surge;
import com.volmit.react.controller.CrashController;
import com.volmit.volume.lang.collections.GList;

public abstract class TPSMonitor extends Thread
{
	private double tickTimeMS;
	private double rawTicksPerSecond;
	private Profiler tickProfiler;
	private Profiler tickTimeProfiler;
	private boolean ticked;
	private State lastState;
	private double actualTickTimeMS;
	private double ltt;
	public long lastTick;
	public boolean frozen;
	private StackTraceElement[] lockedStack;
	private double lmsx;
	private MemoryMonitor memoryMonitor;
	private WorldMonitor worldMonitor;
	private long lt = 0;
	private Profiler syncTickProf;
	public boolean running;
	private double mms;
	public static GList<A> run = null;

	public TPSMonitor(MemoryMonitor memoryMonitor, WorldMonitor worldMonitor)
	{
		run = new GList<A>();
		mms = 0;
		syncTickProf = new Profiler();
		syncTickProf.begin();
		lmsx = 0;
		this.memoryMonitor = memoryMonitor;
		this.worldMonitor = worldMonitor;
		setName("React Monitor");
		tickProfiler = new Profiler();
		tickProfiler.begin();
		tickTimeProfiler = new Profiler();
		tickTimeProfiler.begin();
		actualTickTimeMS = 0;
		tickTimeMS = 0;
		frozen = false;
		ltt = 0;
		ticked = false;
		lastState = State.RUNNABLE;
		lastTick = M.ms();
		lockedStack = null;
		frozen = false;
		lt = TICK.tick;
		setPriority(Thread.MIN_PRIORITY);
		running = true;
	}

	public abstract void onTicked();

	public abstract void onSpike();

	@Override
	public void run()
	{
		while(running && !interrupted())
		{
			lt++;

			if(Surge.getServerThread() != null)
			{
				processState(Surge.getServerThread().getState());
			}

			try
			{
				lastTick = React.instance.sampleController.lastTick > lastTick ? React.instance.sampleController.lastTick : lastTick;
			}

			catch(Exception e)
			{

			}

			if(ticked)
			{
				tickProfiler.end();
				tickTimeMS = tickProfiler.getMilliseconds();
				rawTicksPerSecond = M.clip(1000.0 / ((double) (lmsx + tickTimeMS) / 2.0), 0, 20);
				lmsx = tickTimeMS;
				tickProfiler.reset();
				tickProfiler.begin();
				ticked = false;
				actualTickTimeMS = actualTickTimeMS == 0 ? ltt : actualTickTimeMS;
				ltt = actualTickTimeMS > 0 ? actualTickTimeMS : ltt;
				actualTickTimeMS += mms;
				actualTickTimeMS /= 2.0;
				onTicked();
				actualTickTimeMS = 0;
				lastTick = M.ms();
				frozen = false;
				lockedStack = null;
			}

			else if(M.ms() - lastTick > 900)
			{
				boolean wasntFrozen = !frozen;
				frozen = true;
				rawTicksPerSecond = -(M.ms() - lastTick);
				tickTimeMS = M.ms() - lastTick;

				if(wasntFrozen)
				{
					try
					{
						lockedStack = lockedStack == null ? Surge.getServerThread().getStackTrace() : lockedStack;
						onSpike();
					}

					catch(Throwable e)
					{
						Ex.t(e);
					}
				}

				onTicked();
			}

			try
			{
				memoryMonitor.run();

				if(lt % 250 == 0)
				{
					new A()
					{
						@Override
						public void run()
						{
							if(CrashController.inst != null)
							{
								CrashController.inst.run();
							}

							worldMonitor.run();
						}
					};
				}

				if(lt % 50 == 0)
				{
					new A()
					{
						@Override
						public void run()
						{
							React.instance.sampleController.onTickAsync();
							React.instance.monitorController.onTickAsync();
						}
					};
				}
			}

			catch(Throwable e)
			{
				Ex.t(e);
			}

			long[] ns = {M.ns()};
			boolean[] ran = {false};

			new A()
			{
				@Override
				public void run()
				{
					while(M.ns() - ns[0] < 1000000 && !run.isEmpty())
					{
						ran[0] = true;
						run.pop().run();
					}
				}
			};

			try
			{
				Thread.sleep(1);
			}

			catch(InterruptedException e)
			{
				return;
			}
		}
	}

	public void close()
	{
		this.interrupt();
		running = false;
	}

	private void processState(State state)
	{
		if(state.equals(lastState))
		{
			return;
		}

		if(state.equals(State.BLOCKED))
		{
			return;
		}

		if(!state.equals(State.TIMED_WAITING) && !state.equals(State.RUNNABLE))
		{
			return;
		}

		if(lastState.equals(State.RUNNABLE) && state.equals(State.TIMED_WAITING))
		{
			tickTimeProfiler.end();
			actualTickTimeMS += tickTimeProfiler.getMilliseconds();
			tickTimeProfiler.reset();
		}

		if(lastState.equals(State.TIMED_WAITING) && state.equals(State.RUNNABLE))
		{
			tickTimeProfiler.begin();
		}

		lastState = state;
	}

	public double getTickTimeMS()
	{
		return tickTimeMS;
	}

	public double getRawTicksPerSecond()
	{
		return rawTicksPerSecond;
	}

	public Profiler getTickProfiler()
	{
		return tickProfiler;
	}

	public boolean isTicked()
	{
		return ticked;
	}

	public void markTick()
	{
		syncTickProf.end();
		mms = Math.abs(syncTickProf.getMilliseconds() - 50.0);
		ticked = true;
		syncTickProf.reset();
		syncTickProf.begin();
	}

	public Profiler getTickTimeProfiler()
	{
		return tickTimeProfiler;
	}

	public State getLastState()
	{
		return lastState;
	}

	public double getActualTickTimeMS()
	{
		return actualTickTimeMS;
	}

	public double getLtt()
	{
		return ltt;
	}

	public long getLastTick()
	{
		return lastTick;
	}

	public boolean isFrozen()
	{
		return frozen;
	}

	public StackTraceElement[] getLockedStack()
	{
		return lockedStack;
	}
}
