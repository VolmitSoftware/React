package com.volmit.react.util.nmp;

import java.util.Set;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;

public class TickListSplitter
{
	private final World world;
	private final Set<Object> master;
	private final CatalystHost host;
	private final GMap<Object, Integer> withold;
	private final GMap<Material, Integer> witholdTypes;

	public TickListSplitter(World world)
	{
		this.world = world;
		host = Catalyst.host;
		master = host.getTickList(world);
		witholdTypes = new GMap<>();
		withold = new GMap<>();
	}

	public void unregisterAll()
	{
		witholdTypes.clear();
	}

	public void unregister(Material type)
	{
		witholdTypes.remove(type);
	}

	public void register(Material type, int ticks)
	{
		witholdTypes.put(type, ticks);
	}

	public void dumpAll()
	{
		while(!withold.isEmpty())
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
	}
}
