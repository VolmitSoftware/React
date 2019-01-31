package com.volmit.react.util.nmp;

import java.util.Set;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;
import com.volmit.volume.math.M;

public class TickListSplitter
{
	private final World world;
	private final Set<Object> master;
	private final Set<Object> masterFluid;
	private final CatalystHost host;
	private final GMap<Object, Integer> withold;
	private final GMap<Object, Integer> witholdFluid;
	private final GMap<Material, Integer> witholdTypes;
	private final GMap<Chunk, Integer> witholdChunks;
	private int globalThrottle;

	public TickListSplitter(World world)
	{
		this.world = world;
		host = Catalyst.host;
		master = host.getTickList(world);
		masterFluid = host.getTickListFluid(world);
		witholdTypes = new GMap<>();
		withold = new GMap<>();
		witholdFluid = new GMap<>();
		witholdChunks = new GMap<>();
		setGlobalThrottle(0);
	}

	public int getTickCount()
	{
		return master.size() + masterFluid.size();
	}

	public int getWitheldCount()
	{
		return withold.size() + witholdFluid.size();
	}

	public void setPhysicsSpeed(double d)
	{
		setGlobalThrottle((int) (M.clip(d, 0, 1D) * 20D));
	}

	public double getPhysicsSpeed()
	{
		return 1D - (double) M.clip(globalThrottle, 0, 20D) / 20D;
	}

	public void withold(Chunk c, int cy)
	{
		if(cy > 0)
		{
			witholdChunks.put(c, cy);
		}

		else
		{
			unregister(c);
		}
	}

	public void unregisterAll()
	{
		witholdTypes.clear();
	}

	public void unregister(Material type)
	{
		witholdTypes.remove(type);
	}

	public void unregisterAllChunks()
	{
		witholdChunks.clear();
	}

	public void unregister(Chunk type)
	{
		witholdChunks.remove(type);
	}

	public void setGlobalThrottle(int throttle)
	{
		this.globalThrottle = throttle;
	}

	public void register(Material type, int ticks)
	{
		if(ticks > 0)
		{
			witholdTypes.put(type, ticks);
		}

		else
		{
			unregister(type);
		}
	}

	public void dumpAll()
	{
		while(getWitheldCount() > 0)
		{
			dumpWitheldTickList();
		}
	}

	public void tick()
	{
		for(Object i : new GList<>(master))
		{
			Block b = host.getBlock(world, i);
			Material t = b.getType();

			if(witholdTypes.containsKey(t))
			{
				withold.put(i, witholdTypes.get(t));
				master.remove(i);
			}

			else if(globalThrottle > 0)
			{
				withold.put(i, globalThrottle);
				master.remove(i);
			}

			else if(witholdChunks.containsKey(b.getChunk()))
			{
				withold.put(i, witholdChunks.get(b.getChunk()));
				master.remove(i);
			}
		}

		for(Object i : new GList<>(masterFluid))
		{
			Block b = host.getBlock(world, i);
			Material t = b.getType();

			if(witholdTypes.containsKey(t))
			{
				witholdFluid.put(i, witholdTypes.get(t));
				masterFluid.remove(i);
			}

			else if(globalThrottle > 0)
			{
				witholdFluid.put(i, globalThrottle);
				masterFluid.remove(i);
			}

			else if(witholdChunks.containsKey(b.getChunk()))
			{
				witholdFluid.put(i, witholdChunks.get(b.getChunk()));
				masterFluid.remove(i);
			}
		}

		dumpWitheldTickList();
	}

	private void dumpWitheldTickList()
	{
		for(Object i : withold.k())
		{
			withold.put(i, withold.get(i) - 1);

			if(withold.get(i) <= 0)
			{
				withold.remove(i);
				master.add(i);
			}
		}

		for(Object i : witholdFluid.k())
		{
			witholdFluid.put(i, witholdFluid.get(i) - 1);

			if(witholdFluid.get(i) <= 0)
			{
				witholdFluid.remove(i);
				masterFluid.add(i);
			}
		}
	}
}
