package com.volmit.react.util;

import org.bukkit.Material;
import org.bukkit.block.Block;

import com.volmit.react.util.Cuboid.CuboidDirection;
import com.volmit.volume.lang.collections.GList;

public class BlockFinder
{
	public static boolean follow(Block block, GList<Material> follow, GList<Material> goal, int maxTravel)
	{
		Cuboid c = new Cuboid(block.getLocation());
		c = c.expand(CuboidDirection.Up, maxTravel);
		c = c.expand(CuboidDirection.Down, maxTravel);
		c = c.expand(CuboidDirection.North, maxTravel);
		c = c.expand(CuboidDirection.South, maxTravel);
		c = c.expand(CuboidDirection.East, maxTravel);
		c = c.expand(CuboidDirection.West, maxTravel);

		return tail(c, block, follow, goal, maxTravel);
	}

	public static boolean tail(Cuboid c, Block b, GList<Material> f, GList<Material> g, int t)
	{
		if(t <= 0)
		{
			return false;
		}

		for(Block i : W.blockFaces(b))
		{
			if(f.contains(i.getType()))
			{
				if(tail(c, i, f, g, t - 1))
				{
					return true;
				}
			}

			if(g.contains(i.getType()))
			{
				return true;
			}
		}

		return false;
	}
}
