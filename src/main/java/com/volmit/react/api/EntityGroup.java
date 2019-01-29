package com.volmit.react.api;

import org.bukkit.entity.EntityType;

import com.volmit.volume.lang.collections.GSet;

public class EntityGroup
{
	private GSet<EntityType> entityTypes;

	public EntityGroup()
	{
		entityTypes = new GSet<EntityType>();
	}

	public GSet<EntityType> getEntityTypes()
	{
		return entityTypes;
	}
}
