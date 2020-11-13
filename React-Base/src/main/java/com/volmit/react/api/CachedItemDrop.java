package com.volmit.react.api;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

public class CachedItemDrop extends CachedEntity
{
	private ItemStack is;

	public CachedItemDrop(Item e)
	{
		super(e);

		is = e.getItemStack();
	}

	@Override
	public void apply(Entity ee)
	{
		super.apply(ee);
		Item e = (Item) ee;
		e.setItemStack(is);
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((is == null) ? 0 : is.hashCode());
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
		CachedItemDrop other = (CachedItemDrop) obj;
		if(is == null)
		{
			if(other.is != null)
				return false;
		}
		else if(!is.equals(other.is))
			return false;
		return true;
	}

}
