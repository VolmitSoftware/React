package com.volmit.react.api;

import org.bukkit.command.CommandSender;

public interface IFix
{
	String getId();

	String[] getAliases();

	String getName();

	String getDescription();

	String getUsage();

	void run(CommandSender sender, String[] args);
}
