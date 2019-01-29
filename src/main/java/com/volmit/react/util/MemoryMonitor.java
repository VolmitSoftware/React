package com.volmit.react.util;

public abstract class MemoryMonitor
{
	private long memoryFree;
	private long memoryUsed;
	private long memoryMax;
	private long lastMemoryUsed;
	private long memoryUsedAfterGC;
	private long allocated;
	private long collected;
	private long collections;
	private long sms;
	private long memoryAllocatedPerTick;
	private long memoryCollectedPerTick;
	private long memoryFullyAllocatedPerTick;
	private long mah;
	private long mahs;

	public MemoryMonitor()
	{
		memoryFree = Runtime.getRuntime().freeMemory();
		memoryMax = Runtime.getRuntime().maxMemory();
		memoryUsed = memoryMax - memoryFree;
		lastMemoryUsed = memoryUsed;
		allocated = 0;
		collected = 0;
		mah = 0;
		mahs = 0;
		collections = 0;
		memoryUsedAfterGC = 0;
		sms = M.ms();
		memoryAllocatedPerTick = 0;
		memoryCollectedPerTick = 0;
		memoryFullyAllocatedPerTick = 0;
	}

	public abstract void onAllocationSet();

	public void run()
	{
		memoryMax = Runtime.getRuntime().maxMemory();
		memoryFree = Runtime.getRuntime().freeMemory() + (memoryMax - Runtime.getRuntime().totalMemory());
		memoryUsed = memoryMax - memoryFree;

		if(memoryUsedAfterGC == 0)
		{
			memoryUsedAfterGC = memoryUsed;
		}

		if(memoryUsed >= lastMemoryUsed)
		{
			allocated += memoryUsed - lastMemoryUsed;
			mah += memoryUsed - lastMemoryUsed;
		}

		else
		{
			collected = lastMemoryUsed - memoryUsed;
			memoryUsedAfterGC = memoryUsed;
			allocated = 0;
		}

		lastMemoryUsed = memoryUsed;

		if(M.ms() - sms >= 50)
		{
			sms = M.ms();
			memoryAllocatedPerTick = allocated;
			memoryCollectedPerTick = collected;
			memoryFullyAllocatedPerTick = Math.max(0, memoryAllocatedPerTick - memoryCollectedPerTick);
			mahs = mah * 20;
			mah = 0;
			onAllocationSet();
		}
	}

	public long getMemoryFree()
	{
		return memoryFree;
	}

	public long getMemoryUsed()
	{
		return memoryUsed;
	}

	public long getMemoryMax()
	{
		return memoryMax;
	}

	public long getLastMemoryUsed()
	{
		return lastMemoryUsed;
	}

	public long getMemoryUsedAfterGC()
	{
		return memoryUsedAfterGC;
	}

	public long getAllocated()
	{
		return allocated;
	}

	public long getCollected()
	{
		return collected;
	}

	public long getCollections()
	{
		return collections;
	}

	public long getSms()
	{
		return sms;
	}

	public long getMemoryAllocatedPerTick()
	{
		return memoryAllocatedPerTick;
	}

	public long getMemoryCollectedPerTick()
	{
		return memoryCollectedPerTick;
	}

	public long getMemoryFullyAllocatedPerTick()
	{
		return memoryFullyAllocatedPerTick;
	}

	public long getMah()
	{
		return mah;
	}

	public long getMahs()
	{
		return mahs;
	}
}
