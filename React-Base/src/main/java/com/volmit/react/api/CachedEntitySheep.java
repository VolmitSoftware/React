package com.volmit.react.api;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Sheep;

public class CachedEntitySheep extends CachedEntityColorable
{
	private boolean sheared;

	public CachedEntitySheep(Sheep e)
	{
		super(e);

		sheared = e.isSheared();
	}

	@Override
	public void apply(Entity ee)
	{
		super.apply(ee);

		Sheep s = (Sheep) ee;
		s.setSheared(sheared);
	}

	public boolean isSheared()
	{
		return sheared;
	}

	public void setSheared(boolean sheared)
	{
		this.sheared = sheared;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (sheared ? 1231 : 1237);
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(!super.equals(obj))
			return false;
		if(getClass() != obj.getClass())
			return false;
		CachedEntitySheep other = (CachedEntitySheep) obj;
		if(sheared != other.sheared)
			return false;
		return true;
	}
}
