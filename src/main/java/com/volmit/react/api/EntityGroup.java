package com.volmit.react.api;

import org.bukkit.entity.EntityType;

import primal.lang.collection.GSet;

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
