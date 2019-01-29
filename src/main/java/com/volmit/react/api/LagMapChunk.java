package com.volmit.react.api;

import org.bukkit.Chunk;
import org.bukkit.World;

import com.volmit.react.controller.EventController;
import com.volmit.react.util.Ex;
import com.volmit.volume.lang.collections.GMap;

public class LagMapChunk implements Comparable<LagMapChunk>
{
	private GMap<ChunkIssue, Double> hits;
	private Chunk c;

	public LagMapChunk(Chunk c)
	{
		this.c = c;
		hits = new GMap<ChunkIssue, Double>();
	}

	public GMap<ChunkIssue, Double> getMS()
	{
		GMap<ChunkIssue, Double> k = EventController.map.getGrandTotal();
		GMap<ChunkIssue, Double> m = new GMap<ChunkIssue, Double>();

		try
		{
			for(ChunkIssue type : hits.k())
			{
				if(!hits.containsKey(type) || !k.containsKey(type))
				{
					m.put(type, 0.0);
					continue;
				}

				m.put(type, type.getMS() * (hits.get(type) / k.get(type)));
			}
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}

		return m;
	}

	public double totalMS()
	{
		double ms = 0;

		GMap<ChunkIssue, Double> k = EventController.map.getGrandTotal();

		for(ChunkIssue type : hits.k())
		{
			if(!hits.containsKey(type) || !k.containsKey(type))
			{
				continue;
			}

			ms += type.getMS() * (hits.get(type) / k.get(type));
		}

		if(hits.containsKey(ChunkIssue.PHYSICS) && hits.containsKey(ChunkIssue.REDSTONE))
		{
			ms -= ChunkIssue.PHYSICS.getMS() * (hits.get(ChunkIssue.PHYSICS) / k.get(ChunkIssue.PHYSICS));
		}

		else if(hits.containsKey(ChunkIssue.PHYSICS) && hits.containsKey(ChunkIssue.FLUID))
		{
			ms -= ChunkIssue.PHYSICS.getMS() * (hits.get(ChunkIssue.PHYSICS) / k.get(ChunkIssue.PHYSICS));
		}

		return ms;
	}

	public double getMS(ChunkIssue type)
	{
		GMap<ChunkIssue, Double> k = EventController.map.getGrandTotal();

		if(!hits.containsKey(type) || !k.containsKey(type))
		{
			return 0;
		}

		return type.getMS() * (hits.get(type) / k.get(type));
	}

	public void hit(ChunkIssue type, double amt)
	{
		if(!hits.containsKey(type))
		{
			hits.put(type, 0.0);
		}

		hits.put(type, hits.get(type) + amt);
	}

	public void hit(ChunkIssue type)
	{
		hit(type, 20);
	}

	public int getX()
	{
		return c.getX();
	}

	public int getZ()
	{
		return c.getZ();
	}

	public double totalScore()
	{
		double d = 0;

		for(Double i : getHits().v())
		{
			d += i;
		}

		return d;
	}

	public GMap<ChunkIssue, Double> getHits()
	{
		return hits;
	}

	public void pump()
	{
		for(ChunkIssue i : getHits().k())
		{
			hits.put(i, hits.get(i) / 1.01);

			if(hits.get(i) < 0.5)
			{
				hits.remove(i);
			}
		}
	}

	@Override
	public int compareTo(LagMapChunk o)
	{
		return (int) (1000.0 * (totalScore() - o.totalScore()));
	}

	public Chunk getC()
	{
		return c;
	}

	public World getWorld()
	{
		return c.getWorld();
	}
}
