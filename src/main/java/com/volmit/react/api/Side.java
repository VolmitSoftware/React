package com.volmit.react.api;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public enum Side
{
	PLAYERS("players"),
	CONSOLE("the console");

	private String ss;

	private Side(String s)
	{
		ss = s;
	}

	public String ss()
	{
		return ss;
	}

	public static Side get(CommandSender sender)
	{
		if(sender instanceof Player)
		{
			return Side.PLAYERS;
		}

		return Side.CONSOLE;
	}
}
