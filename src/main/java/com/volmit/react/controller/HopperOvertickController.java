package com.volmit.react.controller;

import org.bukkit.Location;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import com.volmit.react.Config;
import com.volmit.react.Surge;
import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;
import com.volmit.volume.lang.collections.GList;

public class HopperOvertickController extends Controller
{
	private GList<Location> possiblePlunge;

	@Override
	public void dump(JSONObject object)
	{
		object.put("plunging", possiblePlunge.size());
	}

	@Override
	public void start()
	{
		Surge.register(this);
		possiblePlunge = new GList<Location>();
	}

	@Override
	public void stop()
	{
		Surge.unregister(this);
	}

	@Override
	public void tick()
	{
		possiblePlunge.clear();
	}

	public boolean plunge(Hopper h)
	{
		if(!Config.HOPPER_OVERTICK_ENABLE)
		{
			return false;
		}

		if(h.getInventory().firstEmpty() == -1)
		{
			if(possiblePlunge.contains(h.getLocation()))
			{
				return true;
			}

			else
			{
				possiblePlunge.add(h.getLocation());
			}
		}

		else
		{
			possiblePlunge.remove(h.getLocation());
		}

		return false;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(InventoryMoveItemEvent e)
	{
		if(!Config.HOPPER_OVERTICK_ENABLE)
		{
			return;
		}

		boolean tc = false;
		boolean td = false;

		if(e.getDestination().getHolder() instanceof Hopper)
		{
			tc = plunge((Hopper) e.getDestination().getHolder());
		}

		if(e.getSource().getHolder() instanceof Hopper)
		{
			td = plunge((Hopper) e.getSource().getHolder());
		}

		e.setCancelled(tc || td);
	}

	@Override
	public int getInterval()
	{
		return 1;
	}

	@Override
	public boolean isUrgent()
	{
		return true;
	}
}
