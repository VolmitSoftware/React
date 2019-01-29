package com.volmit.react.util.inventory;

import java.util.function.BiFunction;

import org.bukkit.entity.Player;

import com.volmit.react.ReactPlugin;

public class AnvilText
{
	public static void getText(Player p, String def, RString s)
	{
		try
		{
			new AnvilGUI(ReactPlugin.i, p, def, new BiFunction<Player, String, String>()
			{
				@Override
				public String apply(Player t, String u)
				{
					s.onComplete(u);
					t.closeInventory();

					return "";
				}
			});
		}

		catch(Exception e)
		{

		}
	}
}