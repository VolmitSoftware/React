package com.volmit.react.api;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;

import com.volmit.volume.lang.collections.GSet;

public class SelectorEntityType extends Selector
{
	public SelectorEntityType(SelectionMode mode)
	{
		super(EntityType.class, mode);

		for(EntityType i : EntityType.values())
		{
			getPossibilities().add(i);
		}
	}

	public void add(EntityType type)
	{
		getList().add(type);
	}

	@Override
	public int parse(CommandSender sender, String input) throws SelectorParseException
	{
		GSet<EntityType> etx = new GSet<EntityType>();

		if(input.contains("&"))
		{
			for(String i : input.split("&"))
			{
				boolean neg = i.startsWith("!");

				for(EntityType j : parseNode(sender, neg ? i.substring(1) : i))
				{
					if(neg)
					{
						etx.remove(j);
					}

					else
					{
						etx.add(j);
					}
				}
			}
		}

		else
		{
			etx.addAll(parseNode(sender, input));
		}

		getList().clear();
		getPossibilities().addAll(etx);
		getList().addAll(etx);

		return etx.size();
	}

	private GSet<EntityType> parseNode(CommandSender sender, String input) throws SelectorParseException
	{
		GSet<EntityType> et = new GSet<EntityType>();
		boolean found = false;

		if(input.equalsIgnoreCase("*"))
		{
			for(EntityType i : EntityType.values())
			{
				if(i.equals(EntityType.PLAYER))
				{
					continue;
				}

				et.add(i);
			}

			return et;
		}

		else
		{
			for(EntityType i : EntityType.values())
			{
				if(input.equalsIgnoreCase(i.name()))
				{
					et.add(i);
					found = true;
					break;
				}

				if(input.toUpperCase().contains(i.name()))
				{
					et.add(i);
					found = true;
					break;
				}
			}
		}

		if(!found)
		{
			throw new SelectorParseException("Unable to parse entity type: " + input);
		}

		if(et.contains(EntityType.PLAYER))
		{
			throw new SelectorParseException("Unable to select Player Entity Type");
		}

		return et;
	}

	@Override
	public String getName()
	{
		return "entity type";
	}
}
