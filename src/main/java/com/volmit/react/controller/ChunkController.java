package com.volmit.react.controller;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;

import com.volmit.react.Surge;
import com.volmit.react.api.Unused;
import com.volmit.react.util.Average;
import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;
import com.volmit.react.util.M;
import com.volmit.volume.lang.collections.GMap;

public class ChunkController extends Controller
{
	private boolean firstTickList;
	private long firstTick;
	private long lastTick;
	private Average aCSMS;
	private GMap<Plugin, Integer> pluginLoads;
	private GMap<Player, Integer> playerLoads;
	private int serverLoads;

	@Override
	public void dump(JSONObject object)
	{

	}

	@Override
	public void start()
	{
		Surge.register(this);
		firstTickList = false;
		firstTick = M.ns();
		lastTick = M.ns();
		aCSMS = new Average(30);
		playerLoads = new GMap<Player, Integer>();
		pluginLoads = new GMap<Plugin, Integer>();
		serverLoads = 0;
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
	}

	public void on(PlayerQuitEvent e)
	{
		playerLoads.remove(e.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onLoad(ChunkLoadEvent e)
	{
		tickNextTickList();
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

	public GMap<Plugin, Integer> getPluginLoads()
	{
		return pluginLoads;
	}

	public GMap<Player, Integer> getPlayerLoads()
	{
		return playerLoads;
	}

	public int getServerLoads()
	{
		return serverLoads;
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
