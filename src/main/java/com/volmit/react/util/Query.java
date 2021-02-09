package com.volmit.react.util;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.volmit.react.Surge;

public abstract class Query implements Listener
{
	private Player p;

	public Query(Player p)
	{
		this.p = p;
		Surge.register(this);
	}

	public void close()
	{
		Surge.unregister(this);
	}

	public abstract void onMessage(String msg);

	@EventHandler
	public void on(PlayerQuitEvent e)
	{
		if(e.getPlayer().equals(p))
		{
			close();
		}
	}

	@EventHandler
	public void on(PlayerCommandPreprocessEvent e)
	{
		if(e.getPlayer().equals(p))
		{
			close();
		}
	}

	@EventHandler
	public void on(AsyncPlayerChatEvent e)
	{
		if(e.getPlayer().equals(p))
		{
			new S("query")
			{
				@Override
				public void run()
				{
					onMessage(e.getMessage());
					close();
				}
			};

			e.setCancelled(true);
		}
	}
}
