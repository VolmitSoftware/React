package com.volmit.react.controller;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.spigotmc.TickLimiter;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.Surge;
import com.volmit.react.api.Capability;
import com.volmit.react.api.SpecializedTickLimiter;
import com.volmit.react.util.Controller;
import com.volmit.react.util.Ex;
import com.volmit.react.util.JSONObject;
import com.volmit.react.util.NMSX;
import com.volmit.volume.lang.collections.GMap;

public class SmearTickController extends Controller
{
	private GMap<World, SpecializedTickLimiter> etl;
	private GMap<World, SpecializedTickLimiter> ttl;

	@Override
	public void dump(JSONObject object)
	{
		object.put("throttled-world-instances", etl.size() + ttl.size());
	}

	@Override
	public void start()
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return;
		}

		Surge.register(this);
		etl = new GMap<World, SpecializedTickLimiter>();
		ttl = new GMap<World, SpecializedTickLimiter>();

		for(World i : Bukkit.getWorlds())
		{
			try
			{
				witholdWorld(i);
			}

			catch(Throwable e)
			{

			}
		}
	}

	@Override
	public void stop()
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return;
		}

		Surge.unregister(this);
		for(World i : Bukkit.getWorlds())
		{
			try
			{
				releaseWorld(i);
			}

			catch(Throwable e)
			{

			}
		}
	}

	@EventHandler
	public void on(WorldLoadEvent e)
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return;
		}

		try
		{
			witholdWorld(e.getWorld());
		}

		catch(Throwable ex)
		{

		}
	}

	@EventHandler
	public void on(WorldUnloadEvent e)
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return;
		}

		try
		{
			releaseWorld(e.getWorld());
		}

		catch(Throwable e1)
		{
			Ex.t(e1);
		}
	}

	@Override
	public void tick()
	{

	}

	public void witholdWorld(World w) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return;
		}

		Class<?> cworldclass = NMSX.getCBClass("CraftWorld");
		Object theWorld = cworldclass.getMethod("getHandle").invoke(w);
		Field fe = deepFindField(theWorld, "entityLimiter");
		Field ft = deepFindField(theWorld, "tileLimiter");
		SpecializedTickLimiter ste = new SpecializedTickLimiter(50, true);
		SpecializedTickLimiter stt = new SpecializedTickLimiter(50, false);
		fe.setAccessible(true);
		ft.setAccessible(true);
		fe.set(theWorld, ste);
		ft.set(theWorld, stt);
		etl.put(w, ste);
		ttl.put(w, stt);
	}

	public void releaseWorld(World w) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, NoSuchFieldException
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return;
		}

		Class<?> cworldclass = NMSX.getCBClass("CraftWorld");
		Object theWorld = cworldclass.getMethod("getHandle").invoke(w);
		Field fe = deepFindField(theWorld, "entityLimiter");
		Field ft = deepFindField(theWorld, "tileLimiter");
		TickLimiter ste = new TickLimiter(Gate.getEntityTickMax(w));
		TickLimiter stt = new TickLimiter(Gate.getTileTickMax(w));
		fe.setAccessible(true);
		ft.setAccessible(true);
		fe.set(theWorld, ste);
		ft.set(theWorld, stt);
		etl.remove(w);
		ttl.remove(w);
	}

	public double getUniversalEntityTick()
	{
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return -1;
		}

		double mt = 0;

		for(World i : etl.k())
		{
			if(getLastEntityTick(i) < 0)
			{
				continue;
			}

			mt += getLastEntityTick(i);
		}

		return mt;
	}

	public double getUniversalEntityLimit()
	{
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return -1;
		}

		double mt = 0;

		for(World i : etl.k())
		{
			if(getEntityTickLimit(i) < 0)
			{
				continue;
			}

			mt += getEntityTickLimit(i);
		}

		return mt;
	}

	public double getUniversalTileLimit()
	{
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return -1;
		}

		double mt = 0;

		for(World i : etl.k())
		{
			if(getTileTickLimit(i) < 0)
			{
				continue;
			}

			mt += getTileTickLimit(i);
		}

		return mt;
	}

	public double getUniversalTileTick()
	{
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return -1;
		}

		double mt = 0;

		for(World i : ttl.k())
		{
			if(getLastTileTick(i) < 0)
			{
				continue;
			}

			mt += getLastTileTick(i);
		}

		return mt;
	}

	public double getUniversalEntityDroppedTicks()
	{
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return -1;
		}

		double mt = 0;

		for(World i : ttl.k())
		{
			mt += getEntityDroppedTicks(i);
		}

		return mt;
	}

	public double getUniversalTileDroppedTicks()
	{
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return -1;
		}

		double mt = 0;

		for(World i : ttl.k())
		{
			mt += getTileDroppedTicks(i);
		}

		return mt;
	}

	public void setEntityTickLimit(World w, double lim)
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return;
		}

		if(!Config.getWorldConfig(w).allowEntityThrottling)
		{
			return;
		}

		etl.get(w).rMaxTime = lim;
	}

	public void setTileTickLimit(World w, double lim)
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return;
		}

		if(!Config.getWorldConfig(w).allowTileThrottling)
		{
			return;
		}

		ttl.get(w).rMaxTime = lim;
	}

	public double getEntityTickLimit(World w)
	{
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return -1;
		}

		return etl.get(w).rMaxTime;
	}

	public double getTileTickLimit(World w)
	{
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return -1;
		}

		return ttl.get(w).rMaxTime;
	}

	public double getLastEntityTick(World w)
	{
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return -1;
		}

		return etl.get(w).atimes.getAverage();
	}

	public double getLastTileTick(World w)
	{
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return -1;
		}

		return ttl.get(w).atimes.getAverage();
	}

	public double getEntityDroppedTicks(World w)
	{
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return -1;
		}

		return etl.get(w).adropped.getAverage();
	}

	public double getTileDroppedTicks(World w)
	{
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return -1;
		}

		return ttl.get(w).adropped.getAverage();
	}

	public static Field deepFindField(Object obj, String fieldName)
	{
		if(!Capability.ENTITY_THROTTLING.isCapable() || !Capability.TILE_THROTTLING.isCapable())
		{
			return null;
		}

		Class<?> cls = obj.getClass();

		for(Class<?> acls = cls; acls != null; acls = acls.getSuperclass())
		{
			try
			{
				Field field = acls.getDeclaredField(fieldName);

				return field;
			}

			catch(Throwable ex)
			{

			}
		}

		return null;
	}

	@Override
	public int getInterval()
	{
		return 1234;
	}

	@Override
	public boolean isUrgent()
	{
		return false;
	}
}
