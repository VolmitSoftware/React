package com.volmit.react.controller;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;

import com.volmit.react.Config;
import com.volmit.react.Surge;
import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;

public class CollisionController extends Controller
{
	private boolean capable = false;

	@Override
	public void dump(JSONObject object)
	{

	}

	public boolean isCompatable()
	{
		Class<?> c = LivingEntity.class;

		try
		{
			c.getMethod("setCollidable", boolean.class);
			return true;
		}

		catch(NoSuchMethodException | SecurityException e)
		{
			try
			{
				c.getDeclaredMethod("setCollidable", boolean.class);
				return true;
			}

			catch(NoSuchMethodException | SecurityException ex)
			{
				return false;
			}
		}
	}

	@EventHandler
	public void on(EntitySpawnEvent e)
	{
		if(!capable)
		{
			return;
		}

		if(e.getEntity() instanceof LivingEntity)
		{
			entityTick((LivingEntity) e.getEntity());
		}
	}

	@EventHandler
	public void on(SpawnerSpawnEvent e)
	{
		if(!capable)
		{
			return;
		}

		if(Config.USE_COLLISION)
		{
			if(e instanceof LivingEntity && Config.SPAWNER_DISABLE.contains(e.getEntityType().name()))
			{
				((LivingEntity) e.getEntity()).setCollidable(false);
			}
		}
	}

	public void precull(LivingEntity ee)
	{
		if(!capable)
		{
			return;
		}

		if(Config.USE_COLLISION && Config.USE_COLLISION_ON_CULL)
		{
			ee.setCollidable(false);
		}
	}

	public void entityTick(LivingEntity ee)
	{
		if(!capable)
		{
			return;
		}

		if(Config.USE_COLLISION)
		{
			if(Config.ALWAYS_DISABLE.contains(ee.getType().name()) && ee.isCollidable())
			{
				ee.setCollidable(false);
			}
		}
	}

	@Override
	public void start()
	{
		Surge.register(this);
		capable = isCompatable();
	}

	@Override
	public void stop()
	{
		Surge.unregister(this);
	}

	@Override
	public void tick()
	{

	}

	@Override
	public int getInterval()
	{
		return 1200;
	}

	@Override
	public boolean isUrgent()
	{
		return false;
	}
}
