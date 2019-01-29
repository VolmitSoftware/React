package com.volmit.react.api;

import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;
import com.volmit.volume.lang.collections.GSet;

public class EntitySample
{
	private GMap<EntityType, Integer> counts;
	private GMap<EntityType, GSet<Entity>> sets;

	public EntitySample()
	{
		counts = new GMap<EntityType, Integer>();
		sets = new GMap<EntityType, GSet<Entity>>();
	}

	public EntitySample(Chunk c)
	{
		this();

		for(Entity i : c.getEntities())
		{
			add(i);
		}
	}

	public GList<Entity> getAll()
	{
		GList<Entity> ents = new GList<Entity>();

		for(EntityType i : sets.k())
		{
			ents.addAll(sets.get(i));
		}

		return ents;
	}

	public GList<EntityType> getTypes()
	{
		return counts.k();
	}

	public int total()
	{
		int t = 0;

		for(EntityType i : getTypes())
		{
			t += get(i);
		}

		return t;
	}

	public int get(EntityType i)
	{
		if(counts.containsKey(i))
		{
			return counts.get(i);
		}

		return 0;
	}

	public GSet<Entity> getSet(EntityType i)
	{
		GSet<Entity> m = new GSet<Entity>();

		if(sets.containsKey(i))
		{
			m.addAll(sets.get(i));
		}

		return m;
	}

	public void add(Entity i)
	{
		if(!counts.containsKey(i.getType()))
		{
			counts.put(i.getType(), 0);
		}

		if(!sets.containsKey(i.getType()))
		{
			sets.put(i.getType(), new GSet<Entity>());
		}

		sets.get(i.getType()).add(i);
		counts.put(i.getType(), counts.get(i.getType()) + 1);
	}
}
