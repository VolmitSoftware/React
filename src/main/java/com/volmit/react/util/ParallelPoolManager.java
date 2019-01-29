package com.volmit.react.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.api.SampledType;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;

public abstract class ParallelPoolManager
{
	private QueueMode mode;
	private GList<ParallelThread> threads;
	private int next;
	private int threadCount;
	private Queue<Execution> squeue;
	private String key;
	private ThreadInformation info;
	private long lpulse = M.ms();
	private int scd = 50;
	private GMap<String, Average> spikeSuppressor = new GMap<String, Average>();

	public ParallelPoolManager(String key, int threadCount, QueueMode mode)
	{
		this(threadCount, mode);
		this.key = key;
	}

	public ParallelPoolManager(int threadCount, QueueMode mode)
	{
		if(threadCount < 1)
		{
			threadCount = 1;
		}

		if(threadCount > 4)
		{
			System.out.println("WARNING: HIGH THREAD COUNT FOR CORETICK");
		}

		threads = new GList<ParallelThread>();
		this.threadCount = threadCount;
		next = 0;
		this.mode = mode;
		key = "Worker Thread";
		info = new ThreadInformation(-1);
		squeue = new ConcurrentLinkedQueue<Execution>();
	}

	public void syncQueue(Execution e)
	{
		squeue.offer(e);
	}

	public abstract long getNanoGate();

	public void tickSyncQueue()
	{
		tickSyncQueue(false);
	}

	public void tickSyncQueue(boolean force)
	{
		try
		{
			if(scd > 0)
			{
				scd--;

				long ns = M.ns();
				double nsl = (0.237) * 1000000.0;

				while(!squeue.isEmpty() && M.ns() - ns < nsl)
				{
					burn();
				}

				return;
			}

			if(!squeue.isEmpty())
			{
				burn();
			}

			if(force && M.ms() - lpulse < 337)
			{
				force = false;
			}

			else
			{
				lpulse = M.ms();
			}

			if(squeue.size() > (79))
			{
				burnSection(0.25);
			}

			else if(squeue.size() > (30))
			{
				if(Gate.isLowMemory())
				{
					if(TICK.tick % 20 == 0)
					{
						burnSection(0.01);
					}
				}

				else
				{
					burnSection(0.01);
				}
			}

			if(SampledType.TPS.get().getValue() < 19.59)
			{
				return;
			}

			if(squeue.size() > (30) && TICK.tick % 5 == 0)
			{
				long ns = M.ns();
				double nsl = (force ? 0.26 : 0.008) * 1000000.0;

				while(!squeue.isEmpty() && M.ns() - ns < nsl)
				{
					burn();
				}
			}

			Gate.stable = squeue.size() < 75;
		}

		catch(Throwable e)
		{

		}
	}

	private void burnSection(double maxEstimatedMS)
	{
		if(!Config.QUEUE_SUPPRESSION)
		{
			return;
		}

		double estimatedTime = 0;
		double consumed = 0;

		GList<Execution> poll = new GList<Execution>();

		for(Execution i : squeue)
		{
			if(i.idv != null && spikeSuppressor.containsKey(i.idv))
			{
				if(estimatedTime + spikeSuppressor.get(i.idv).getAverage() < maxEstimatedMS)
				{
					poll.add(i);
					estimatedTime += spikeSuppressor.get(i.idv).getAverage();
				}
			}
		}

		for(Execution i : poll)
		{
			consumed += burn(i);
			squeue.remove(i);

			if(consumed > maxEstimatedMS)
			{
				return;
			}
		}
	}

	private void burn()
	{
		burn(squeue.poll());
	}

	private double burn(Execution ee)
	{
		I.a("sync-queue.rawtick", 100);
		Profiler pr = new Profiler();
		pr.begin();

		try
		{
			ee.run();
			pr.end();

			if(ee.idv != null)
			{
				if(!spikeSuppressor.containsKey(ee.idv))
				{
					spikeSuppressor.put(ee.idv, new Average(200));
				}

				spikeSuppressor.get(ee.idv).put(pr.getMilliseconds());
			}
		}

		catch(Throwable e)
		{

		}

		if(pr.isProfiling())
		{
			pr.end();
		}

		I.b("sync-queue.rawtick");
		return pr.getMilliseconds();
	}

	public long lock()
	{
		long k = M.ms();

		while(getTotalQueueSize() > 0)
		{
			try
			{
				Thread.sleep(1);
			}

			catch(Throwable e)
			{
				Ex.t(e);
			}
		}

		return M.ms() - k;
	}

	public int getTotalQueueSize()
	{
		int size = getQueueSize();

		for(ParallelThread i : threads)
		{
			size += i.getQueue().size();
		}

		return size;
	}

	public void start()
	{
		createThreads(threadCount);
	}

	public void shutdown()
	{
		for(ParallelThread i : threads)
		{
			i.interrupt();
		}
	}

	public ParallelPoolManager(int threadCount)
	{
		this(threadCount, QueueMode.ROUND_ROBIN);
	}

	public void queue(Execution e)
	{
		nextThread().queue(e);
	}

	public int getSize()
	{
		return threads.size();
	}

	public int getQueueSize()
	{
		int s = 0;

		for(ParallelThread i : getThreads())
		{
			s += i.getQueue().size();
		}

		return s;
	}

	public ParallelThread[] getThreads()
	{
		return threads.toArray(new ParallelThread[threads.size()]);
	}

	private void updateThreadInformation()
	{
		try
		{
			if(threads.isEmpty())
			{
				return;
			}

			double ticksPerSecond = 0;
			int queuedSize = 0;
			double utilization = 0;

			for(ParallelThread ph : threads.copy())
			{
				ticksPerSecond += ph.getInfo().getTicksPerSecondAverage();
				queuedSize += ph.getQueue().size();
				utilization += ph.getInfo().getUtilization();
			}

			utilization /= threads.size();
			ticksPerSecond /= threads.size();
			getAverageInfo().setTicksPerSecond(ticksPerSecond);
			getAverageInfo().setQueuedSize(queuedSize);
			getAverageInfo().setUtilization(utilization);
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	private ParallelThread nextThread()
	{
		updateThreadInformation();

		if(threads.size() == 1)
		{
			return threads.get(0);
		}

		int id = 0;

		switch(mode)
		{
			case ROUND_ROBIN:
				next = (next > threads.size() - 1 ? 0 : next + 1);
				id = next;
			case SMALLEST:
				int min = Integer.MAX_VALUE;

				for(ParallelThread i : threads)
				{
					int size = i.getQueue().size();

					if(size < min)
					{
						min = size;
						id = i.getInfo().getId();
					}
				}

			default:
				break;
		}

		return threads.get(id);
	}

	private void createThreads(int count)
	{

	}

	public QueueMode getMode()
	{
		return mode;
	}

	public int getNext()
	{
		return next;
	}

	public int getThreadCount()
	{
		return threadCount;
	}

	public Queue<Execution> getSqueue()
	{
		return squeue;
	}

	public String getKey()
	{
		return key;
	}

	public ThreadInformation getAverageInfo()
	{
		return info;
	}
}
