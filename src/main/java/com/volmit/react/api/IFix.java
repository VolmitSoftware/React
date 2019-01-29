package com.volmit.react.api;

import org.bukkit.command.CommandSender;

public interface IFix
{
	public String getId();

	public String[] getAliases();

	public String getName();

	public String getDescription();

	public String getUsage();

	public void run(CommandSender sender, String[] args);
}
