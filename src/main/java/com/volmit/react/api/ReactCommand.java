package com.volmit.react.api;

import com.volmit.volume.lang.collections.GMap;

public abstract class ReactCommand implements ICommand
{
	protected String command;
	protected String[] aliases;
	protected String[] permissions;
	protected String usage;
	protected String description;
	protected GMap<String, String> parameterDescriptions;
	protected SideGate sideGate;

	public ReactCommand()
	{
		parameterDescriptions = new GMap<String, String>();
	}

	@Override
	public String getDescriptionForParameter(String par)
	{
		return parameterDescriptions.get(par.toLowerCase());
	}

	@Override
	public void registerParameterDescription(String id, String desc)
	{
		parameterDescriptions.put(id, desc);
	}

	@Override
	public String getCommand()
	{
		return command;
	}

	@Override
	public String[] getAliases()
	{
		return aliases;
	}

	@Override
	public String[] getPermissions()
	{
		return permissions;
	}

	@Override
	public String getUsage()
	{
		return usage;
	}

	@Override
	public String getDescription()
	{
		return description;
	}

	@Override
	public SideGate getSideGate()
	{
		return sideGate;
	}
}
