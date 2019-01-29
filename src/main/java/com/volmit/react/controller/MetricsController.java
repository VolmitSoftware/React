package com.volmit.react.controller;

import java.util.concurrent.Callable;

import org.bukkit.Bukkit;

import com.volmit.react.Config;
import com.volmit.react.ReactPlugin;
import com.volmit.react.api.BStats;
import com.volmit.react.api.SampledType;
import com.volmit.react.api.Unused;
import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;

public class MetricsController extends Controller
{
	private BStats stats;

	@Override
	public void dump(JSONObject object)
	{

	}

	@Override
	public void start()
	{
		if(Config.SAFE_MODE_NETWORKING)
		{
			return;
		}

		stats = new BStats(ReactPlugin.i);

		stats.addCustomChart(new BStats.SimplePie("max_memory", new Callable<String>()
		{
			@Override
			public String call() throws Exception
			{
				return SampledType.MAXMEM.get().get();
			}
		}));

		stats.addCustomChart(new BStats.SimplePie("language", new Callable<String>()
		{
			@Override
			public String call() throws Exception
			{
				return Config.LANGUAGE;
			}
		}));

		stats.addCustomChart(new BStats.SimplePie("view_distance", new Callable<String>()
		{
			@Override
			public String call() throws Exception
			{
				return Bukkit.getServer().getViewDistance() + "";
			}
		}));

		stats.addCustomChart(new BStats.SimplePie("using_protocollib", new Callable<String>()
		{
			@Override
			public String call() throws Exception
			{
				return Bukkit.getPluginManager().getPlugin("ProtocolLib") != null ? "Yes" : "No";
			}
		}));

		stats.addCustomChart(new BStats.SimplePie("using_fawe", new Callable<String>()
		{
			@Override
			public String call() throws Exception
			{
				return Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null ? "Yes" : "No";
			}
		}));
	}

	@Override
	public void stop()
	{

	}

	@Unused
	@Override
	public void tick()
	{

	}

	@Override
	public int getInterval()
	{
		return 2016;
	}

	@Override
	public boolean isUrgent()
	{
		return false;
	}
}
