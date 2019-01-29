package com.volmit.react.controller;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.WorldUnloadEvent;

import com.volmit.react.Config;
import com.volmit.react.ReactPlugin;
import com.volmit.react.Surge;
import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;

public class WorldController extends Controller
{
	private boolean aboutToWipe;

	@Override
	public void dump(JSONObject object)
	{

	}

	@Override
	public void start()
	{
		aboutToWipe = false;
		Surge.register(this);

		for(World i : Bukkit.getWorlds())
		{
			Config.getWorldConfig(i);
		}
	}

	@Override
	public void stop()
	{
		Surge.unregister(this);

		for(World i : Bukkit.getWorlds())
		{
			Config.closeWorldConfig(i);
		}

		if(aboutToWipe)
		{
			File fx = new File(ReactPlugin.i.getDataFolder(), "worlds");

			for(File i : fx.listFiles())
			{
				i.delete();
			}
		}
	}

	@Override
	public void tick()
	{

	}

	@EventHandler
	public void on(WorldUnloadEvent e)
	{
		Config.closeWorldConfig(e.getWorld());
	}

	public void wipe()
	{
		aboutToWipe = true;
	}

	@Override
	public int getInterval()
	{
		return 1256;
	}

	@Override
	public boolean isUrgent()
	{
		return false;
	}
}
