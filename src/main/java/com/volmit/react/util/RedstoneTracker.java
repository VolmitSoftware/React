package com.volmit.react.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.React;
import com.volmit.react.Surge;
import com.volmit.react.api.SampledType;
import com.volmit.react.controller.FeatureController;
import com.volmit.volume.lang.collections.GList;

public class RedstoneTracker implements Listener
{
	private World world;
	private FeatureController fc;
	private GList<RedstoneEvent> events;
	private boolean ticking;
	private Task task;
	private boolean nms;
	private int tr;
	private Average aav = new Average(30);
	private boolean handleTicking;

	public RedstoneTracker(World world)
	{
		Surge.register(this);
		this.world = world;
		handleTicking = true;
		nms = false;
		fc = React.instance.featureController;
		events = new GList<RedstoneEvent>();
		ticking = false;
		tr = 10;

		task = new Task("rst-" + world.getUID().toString(), 0)
		{
			@Override
			public void run()
			{
				tick();
			}
		};
	}

	public void close()
	{
		task.cancel();
		Surge.unregister(this);
	}

	public void tick()
	{
		if(!Config.REDSTONE_DYNAMIC_CLOCK)
		{
			return;
		}

		if(!handleTicking)
		{
			return;
		}

		aav.put(SampledType.REDSTONE_TICK_USAGE.get().getValue());

		if(aav.getAverage() < 15)
		{
			tr = (int) (2 + (aav.getAverage() * 5));
		}

		else
		{
			tr = (int) (2 + (aav.getAverage() * 40));
		}

		if(TICK.tick % tr == 0)
		{
			ticking = true;

			for(int i = 0; i < events.size(); i++)
			{
				enact(events.pop());
			}

			ticking = false;
		}
	}

	@SuppressWarnings("deprecation")
	public void set(Location l, MaterialBlock mb)
	{
		if(!Config.REDSTONE_DYNAMIC_CLOCK)
		{
			return;
		}

		if(!handleTicking)
		{
			return;
		}

		if(nms)
		{
			fc.setBlock(l, mb);
		}

		else
		{
			Profiler p = new Profiler();
			p.begin();
			l.getBlock().setType(mb.getMaterial());
			l.getBlock().setData(mb.getData());
			Gate.updateBlock(l.getBlock());
			p.end();
		}
	}

	public void enact(RedstoneEvent e)
	{
		if(!Config.REDSTONE_DYNAMIC_CLOCK)
		{
			return;
		}

		if(!handleTicking)
		{
			return;
		}

		Block b = world.getBlockAt(e.getVector().getBlockX(), e.getVector().getBlockY(), e.getVector().getBlockZ());
		MaterialBlock mb = new MaterialBlock(b);

		if(e.getBlock().getMaterial().equals(Material.REDSTONE_WIRE))
		{
			if(mb.equals(e.getBlock()))
			{
				MaterialBlock goal = new MaterialBlock(e.getBlock().getMaterial(), e.getTo());
				set(b.getLocation(), goal);
			}
		}
	}

	@EventHandler
	public void on(BlockRedstoneEvent e)
	{
		if(!Config.REDSTONE_DYNAMIC_CLOCK)
		{
			return;
		}

		if(!e.getBlock().getWorld().equals(world))
		{
			return;
		}

		if(!handleTicking)
		{
			return;
		}

		if(ticking)
		{
			return;
		}

		Material m = e.getBlock().getType();

		if(m.equals(Material.REDSTONE_WIRE))
		{
			events.add(new RedstoneEvent(e));
			e.setNewCurrent(e.getOldCurrent());
		}
	}
}
