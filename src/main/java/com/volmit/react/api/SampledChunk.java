package com.volmit.react.api;

import org.bukkit.Chunk;

import com.volmit.react.util.Ex;
import com.volmit.volume.lang.collections.GMap;

public class SampledChunk
{
	private Chunk c;
	private GMap<ChunkIssue, Integer> counts;

	public SampledChunk(Chunk c)
	{
		this.c = c;
		counts = new GMap<ChunkIssue, Integer>();
	}

	public void dec()
	{
		counts.clear();
	}

	public void hit(ChunkIssue issue, int weight)
	{
		try
		{
			if(!counts.containsKey(issue))
			{
				counts.put(issue, 0);
			}

			counts.put(issue, counts.get(issue) + weight);
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	public Chunk getC()
	{
		return c;
	}

	public GMap<ChunkIssue, Integer> getCounts()
	{
		return counts;
	}
}
