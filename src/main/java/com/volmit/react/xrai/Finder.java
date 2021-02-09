package com.volmit.react.xrai;

import org.bukkit.Chunk;

import com.volmit.react.api.ChunkIssue;
import com.volmit.react.api.LagMap;
import com.volmit.react.controller.EventController;
import com.volmit.react.util.Chunks;

public class Finder
{
	public static Chunk highEntities()
	{
		return high(ChunkIssue.ENTITY);
	}

	public static Chunk highRedstone()
	{
		return high(ChunkIssue.REDSTONE);
	}

	public static Chunk highFluid()
	{
		return high(ChunkIssue.FLUID);
	}

	public static Chunk highHopper()
	{
		return high(ChunkIssue.HOPPER);
	}

	public static Chunk highPhysics()
	{
		return high(ChunkIssue.PHYSICS);
	}

	public static Chunk highTnt()
	{
		return high(ChunkIssue.TNT);
	}

	public static Chunk high(ChunkIssue ci)
	{
		double max = Double.MIN_VALUE;
		Chunk cx = null;

		if(ci.equals(ChunkIssue.ENTITY))
		{
			try
			{
				for(Chunk i : Chunks.getLoadedChunks())
				{
					int c = i.getEntities().length;

					if(c > max)
					{
						max = c;
						cx = i;
					}
				}
			}

			catch(Throwable e)
			{

			}

			return cx;
		}

		LagMap lm = EventController.map;

		for(Chunk i : lm.getChunks().k())
		{
			if(lm.getChunks().get(i).getHits().containsKey(ci))
			{
				double v = (lm.getChunks().get(i).getHits().get(ci));

				if(v > max)
				{
					max = v;
					cx = i;
				}
			}
		}

		return cx;
	}
}
