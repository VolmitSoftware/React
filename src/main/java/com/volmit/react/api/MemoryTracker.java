package com.volmit.react.api;

public class MemoryTracker
{
	private long current;
	private long last;
	private boolean gcd;
	private long totalCollected;
	private long totalAllocated;
	private long collected;
	private long allocated;
	private long lastCol;
	private long lastAll;

	public MemoryTracker()
	{
		current = getMemory();
		last = current;
		allocated = 0;
		collected = 0;
		totalAllocated = 0;
		totalCollected = 0;
		lastCol = 0;
		lastAll = 0;
		gcd = false;
	}

	public long getMemory()
	{
		Runtime r = Runtime.getRuntime();
		return r.totalMemory() - r.freeMemory();
	}

	public void tick()
	{
		last = current;
		current = getMemory();

		if(last > current)
		{
			collected = last - current;
			totalCollected += last - current;
			gcd = true;
		}

		else
		{
			allocated += current - last;
			totalAllocated += current - last;
		}

		if(gcd)
		{
			lastCol = collected;
			lastAll = allocated;
			collected = 0;
			allocated = 0;
		}
	}

	public long getCurrent()
	{
		return current;
	}

	public void setCurrent(long current)
	{
		this.current = current;
	}

	public long getLast()
	{
		return last;
	}

	public void setLast(long last)
	{
		this.last = last;
	}

	public boolean isGcd()
	{
		return gcd;
	}

	public void setGcd(boolean gcd)
	{
		this.gcd = gcd;
	}

	public long getTotalCollected()
	{
		return totalCollected;
	}

	public long getTotalAllocated()
	{
		return totalAllocated;
	}

	public long getCollected()
	{
		return collected;
	}

	public void setCollected(long collected)
	{
		this.collected = collected;
	}

	public long getAllocated()
	{
		return allocated;
	}

	public void setAllocated(long allocated)
	{
		this.allocated = allocated;
	}

	public long getLastCol()
	{
		return lastCol;
	}

	public void setLastCol(long lastCol)
	{
		this.lastCol = lastCol;
	}

	public long getLastAll()
	{
		return lastAll;
	}

	public void setLastAll(long lastAll)
	{
		this.lastAll = lastAll;
	}
}
