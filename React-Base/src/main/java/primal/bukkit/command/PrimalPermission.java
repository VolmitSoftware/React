package primal.bukkit.command;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import primal.lang.collection.GList;

public abstract class PrimalPermission
{
	private PrimalPermission parent;

	public PrimalPermission()
	{
		for(Field i : getClass().getDeclaredFields())
		{
			if(i.isAnnotationPresent(Permission.class))
			{
				try
				{
					PrimalPermission px = (PrimalPermission) i.getType().getConstructor().newInstance();
					px.setParent(this);
					i.set(Modifier.isStatic(i.getModifiers()) ? null : this, px);
				}

				catch(IllegalArgumentException | IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException | SecurityException e)
				{
					e.printStackTrace();
				}
			}
		}
	}

	public GList<PrimalPermission> getChildren()
	{
		GList<PrimalPermission> p = new GList<>();

		for(Field i : getClass().getDeclaredFields())
		{
			if(i.isAnnotationPresent(Permission.class))
			{
				try
				{
					p.add((PrimalPermission) i.get(Modifier.isStatic(i.getModifiers()) ? null : this));
				}

				catch(IllegalArgumentException | IllegalAccessException | SecurityException e)
				{
					e.printStackTrace();
				}
			}
		}

		return p;
	}

	public String getFullNode()
	{
		if(hasParent())
		{
			return getParent().getFullNode() + "." + getNode();
		}

		return getNode();
	}

	protected abstract String getNode();

	public abstract String getDescription();

	public abstract boolean isDefault();

	@Override
	public String toString()
	{
		return getFullNode();
	}

	public boolean hasParent()
	{
		return getParent() != null;
	}

	public PrimalPermission getParent()
	{
		return parent;
	}

	public void setParent(PrimalPermission parent)
	{
		this.parent = parent;
	}
}
