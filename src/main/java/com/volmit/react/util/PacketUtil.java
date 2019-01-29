package com.volmit.react.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PacketUtil
{
	public enum V
	{
		R17,
		R18,
		R19,
		R111,
		R110,
		R112;
	}

	public static V getVersion()
	{
		if(Bukkit.getBukkitVersion().startsWith("1.11"))
		{
			return V.R111;
		}

		if(Bukkit.getBukkitVersion().startsWith("1.12"))
		{
			return V.R112;
		}

		if(Bukkit.getBukkitVersion().startsWith("1.10"))
		{
			return V.R110;
		}

		if(Bukkit.getBukkitVersion().startsWith("1.9"))
		{
			return V.R19;
		}

		if(Bukkit.getBukkitVersion().startsWith("1.8"))
		{
			return V.R18;
		}

		return V.R17;
	}

	public static void sendTitle(Player player, Integer in, Integer stay, Integer out, String title, String subTitle)
	{
		if(getVersion().equals(V.R17))
		{
			return;
		}

		try
		{
			NMSX.sendTitle(player, in, stay, out, title, subTitle);
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	public static void clearTitle(Player player)
	{
		if(getVersion().equals(V.R17))
		{
			return;
		}

		try
		{
			NMSX.sendTitle(player, 20, 20, 10, " ", " ");
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	public static void sendActionBar(Player player, String message)
	{
		if(getVersion().equals(V.R17))
		{
			return;
		}

		try
		{
			NMSX.sendActionBar(player, message);
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}
}
