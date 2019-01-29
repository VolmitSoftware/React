package com.volmit.react.api;

import org.bukkit.entity.Entity;

import com.volmit.volume.lang.collections.GSet;

public class CullList
{
	private GSet<Entity> toCull;

	public CullList()
	{
		toCull = new GSet<Entity>();
	}

	public void add(Entity i)
	{
		toCull.add(i);
	}

	public boolean hasNext()
	{
		return !toCull.isEmpty();
	}

	public Entity pop()
	{
		Entity e = toCull.iterator().next();
		toCull.remove(e);
		return e;
	}
}
