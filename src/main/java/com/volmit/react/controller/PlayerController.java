package com.volmit.react.controller;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

import com.volmit.react.Surge;
import com.volmit.react.api.ReactPlayer;
import com.volmit.react.util.A;
import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GSet;

public class PlayerController extends Controller
{
	private GList<ReactPlayer> players;
	private GSet<ReactPlayer> save;

	@Override
	public void dump(JSONObject object)
	{
		object.put("active-react-players", players.size());
	}

	@Override
	public void start()
	{
		players = new GList<ReactPlayer>();
		save = new GSet<ReactPlayer>();
		Surge.register(this);
	}

	@Override
	public void stop()
	{
		save.addAll(players);

		for(ReactPlayer i : new GList<ReactPlayer>(save))
		{
			requestSave(i.getP(), true);
		}

		save.clear();

		Surge.unregister(this);
	}

	@Override
	public void tick()
	{
		GList<ReactPlayer> toSave = new GList<ReactPlayer>(save);
		save.clear();

		new A()
		{
			@Override
			public void run()
			{
				for(ReactPlayer i : toSave)
				{
					requestSave(i.getP(), true);
				}
			}
		};
	}

	public boolean has(Player p)
	{
		for(ReactPlayer i : players)
		{
			if(i.getP().equals(p))
			{
				return true;
			}
		}

		return false;
	}

	public void requestSave(Player p, boolean thisFuckingInstant)
	{
		if(has(p))
		{
			if(thisFuckingInstant)
			{
				save.remove(getPlayer(p));
				getPlayer(p).save();
			}

			else
			{
				save.add(getPlayer(p));
			}
		}
	}

	public ReactPlayer getPlayer(Player p)
	{
		for(ReactPlayer i : players)
		{
			if(i.getP().equals(p))
			{
				return i;
			}
		}

		ReactPlayer rp = new ReactPlayer(p);
		players.add(rp);

		return rp;
	}

	public GList<ReactPlayer> getPlayers()
	{
		return players;
	}

	@EventHandler
	public void on(PlayerQuitEvent e)
	{
		if(has(e.getPlayer()))
		{
			getPlayer(e.getPlayer()).save();
			players.remove(getPlayer(e.getPlayer()));
		}
	}

	@Override
	public int getInterval()
	{
		return 65;
	}

	@Override
	public boolean isUrgent()
	{
		return false;
	}
}
