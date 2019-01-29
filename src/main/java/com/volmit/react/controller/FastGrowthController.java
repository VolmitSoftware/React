package com.volmit.react.controller;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockGrowEvent;

import com.volmit.react.Config;
import com.volmit.react.React;
import com.volmit.react.Surge;
import com.volmit.react.api.Unused;
import com.volmit.react.util.Average;
import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;
import com.volmit.react.util.M;
import com.volmit.react.util.MaterialBlock;
import com.volmit.react.util.TICK;
import com.volmit.volume.lang.collections.GMap;

public class FastGrowthController extends Controller
{
	private boolean firstTickList;
	private long firstTick;
	private long lastTick;
	private Average aCSMS;
	private GMap<Location, MaterialBlock> changes;

	@Override
	public void dump(JSONObject object)
	{
		object.put("queue", changes.size());
	}

	@Override
	public void start()
	{
		Surge.register(this);
		firstTickList = false;
		firstTick = M.ns();
		lastTick = M.ns();
		aCSMS = new Average(30);
		changes = new GMap<Location, MaterialBlock>();
	}

	private void flushTickList()
	{
		if(firstTickList == false)
		{
			aCSMS.put(0);
			return;
		}

		if(lastTick < firstTick)
		{
			firstTick = lastTick;
		}

		aCSMS.put(lastTick - firstTick);
		firstTickList = false;
	}

	private void tickNextTickList()
	{
		if(!firstTickList)
		{
			firstTickList = true;
			firstTick = M.ns();
		}

		else
		{
			lastTick = M.ns();
		}
	}

	@Override
	public void stop()
	{
		Surge.unregister(this);
	}

	@Unused
	@Override
	public void tick()
	{
		flushTickList();

		if(TICK.tick % 5 == 0)
		{
			for(Location i : changes.k())
			{
				React.instance.featureController.setBlock(i, changes.get(i));
			}

			changes.clear();
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(BlockGrowEvent e)
	{
		tickNextTickList();
		// fastApply(e);
	}

	@SuppressWarnings("deprecation")
	public void fastApply(BlockGrowEvent e)
	{
		if(!Config.FAST_GROWTH)
		{
			return;
		}

		e.setCancelled(true);
		MaterialBlock nb = new MaterialBlock(Material.getMaterial(e.getNewState().getTypeId()), e.getNewState().getRawData());
		changes.put(e.getBlock().getLocation(), nb);
	}

	public boolean isFirstTickList()
	{
		return firstTickList;
	}

	public long getFirstTick()
	{
		return firstTick;
	}

	public long getLastTick()
	{
		return lastTick;
	}

	public Average getaCSMS()
	{
		return aCSMS;
	}

	@Override
	public int getInterval()
	{
		return 1;
	}

	@Override
	public boolean isUrgent()
	{
		return false;
	}
}
