package com.volmit.react.api;

import org.bukkit.command.CommandSender;

public class SelectorTime extends Selector
{
	public SelectorTime()
	{
		super(Long.class, SelectionMode.BLACKLIST);
	}

	public void set(Long c)
	{
		getPossibilities().clear();
		getPossibilities().add(c);
	}

	public long get()
	{
		return getPossibilities().isEmpty() ? 10000 : (Long) getPossibilities().iterator().next();
	}

	@Override
	public int parse(CommandSender sender, String input) throws SelectorParseException
	{
		long ms = 10000;
		double mul = 1;
		String tag = "";

		if(input.toLowerCase().endsWith("ms"))
		{
			mul = 1;
			tag = "ms";
		}

		else if(input.toLowerCase().endsWith("t"))
		{
			mul = 50;
			tag = "t";
		}

		else if(input.toLowerCase().endsWith("s"))
		{
			mul = 1000;
			tag = "s";
		}

		else if(input.toLowerCase().endsWith("m"))
		{
			mul = 1000 * 60;
			tag = "m";
		}

		else if(input.toLowerCase().endsWith("h"))
		{
			mul = 1000 * 60 * 60;
			tag = "h";
		}

		else if(input.toLowerCase().endsWith("d"))
		{
			mul = 1000 * 60 * 60 * 24;
			tag = "d";
		}

		String trimmed = input.toLowerCase().substring(0, input.length() - tag.length()).trim();

		try
		{
			double d = Double.valueOf(trimmed);
			ms = (long) (d * mul);
		}

		catch(NumberFormatException e)
		{
			throw new SelectorParseException("Unable to parse: " + input + " for number " + trimmed);
		}

		set(ms);

		return (int) get();
	}

	@Override
	public String getName()
	{
		return "time";
	}
}
