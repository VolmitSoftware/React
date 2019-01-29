package com.volmit.react.api;

import org.bukkit.command.CommandSender;

import com.volmit.volume.lang.collections.GSet;

public abstract class Selector implements ISelector
{
	private Class<?> objectType;
	private SelectionMode mode;
	private GSet<Object> list;
	private GSet<Object> possibilities;
	private static int popoff = 0;

	public Selector(Class<?> objectType, SelectionMode mode)
	{
		this.objectType = objectType;
		this.mode = mode;
		list = new GSet<Object>();
		possibilities = new GSet<Object>();
	}

	@Override
	public SelectionMode getMode()
	{
		return mode;
	}

	@Override
	public Class<?> getType()
	{
		return objectType;
	}

	@Override
	public boolean can(Object o)
	{
		return (getMode().equals(SelectionMode.WHITELIST) && getList().contains(o)) || (getMode().equals(SelectionMode.BLACKLIST) && !getList().contains(o));
	}

	@Override
	public GSet<Object> getList()
	{
		return list;
	}

	@Override
	public GSet<Object> getPossibilities()
	{
		return possibilities;
	}

	@Override
	public abstract int parse(CommandSender sender, String input) throws SelectorParseException;

	public static ISelector createSelector(CommandSender sender, String val) throws SelectorParseException
	{
		if(!val.contains("@"))
		{
			throw new SelectorParseException("MISSING \"@\" All params must start with @<key>:<opts>");
		}

		if(!val.contains(":"))
		{
			throw new SelectorParseException("MISSING \":\" All params must start with @<key>:<opts>");
		}

		String key = val.split(":")[0].substring(1);
		String parse = val.split(":")[1];
		ISelector is = null;

		if(key.equals("c"))
		{
			is = new SelectorPosition();
		}

		else if(key.equals("t"))
		{
			is = new SelectorTime();
		}

		else if(key.equals("e"))
		{
			is = new SelectorEntityType(SelectionMode.WHITELIST);
		}

		else
		{
			throw new SelectorParseException("INVALID KEY: \"" + key + "\"");
		}

		popoff += is.parse(sender, parse);

		return is;
	}

	public static int pop()
	{
		int p = popoff;
		popoff = 0;
		return p;
	}
}
