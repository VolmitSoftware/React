package com.volmit.react.api;

import org.bukkit.DyeColor;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.material.Colorable;

public class CachedEntityColorable extends CachedEntityAgeable
{
	private DyeColor dye;

	public CachedEntityColorable(Colorable e)
	{
		super((Ageable) e);

		dye = e.getColor();
	}

	@Override
	public void apply(Entity ee)
	{
		super.apply(ee);

		Colorable c = (Colorable) ee;
		c.setColor(dye);
	}

	public DyeColor getDye()
	{
		return dye;
	}

	public void setDye(DyeColor dye)
	{
		this.dye = dye;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((dye == null) ? 0 : dye.hashCode());
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
		CachedEntityColorable other = (CachedEntityColorable) obj;
		if(dye != other.dye)
			return false;
		return true;
	}

}
