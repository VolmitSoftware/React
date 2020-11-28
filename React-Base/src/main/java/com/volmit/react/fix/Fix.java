package com.volmit.react.fix;

import com.volmit.react.api.IFix;

public abstract class Fix implements IFix
{
	private String id;
	private String[] aliases;
	private String name;
	private String description;
	private String usage;

	public Fix()
	{
		id = "null";
		aliases = new String[] {"null"};
		name = "null";
		description = "null";
		usage = "null";
	}

	public void setId(String id)
	{
		this.id = id;
	}

	public void setAliases(String[] aliases)
	{
		this.aliases = aliases;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public void setDescription(String description)
	{
		this.description = description;
	}

	public void setUsage(String usage)
	{
		this.usage = usage;
	}

	@Override
	public String getId()
	{
		return id;
	}

	@Override
	public String[] getAliases()
	{
		return aliases;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public String getDescription()
	{
		return description;
	}

	@Override
	public String getUsage()
	{
		return usage;
	}
}
