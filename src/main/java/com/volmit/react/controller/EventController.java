package com.volmit.react.controller;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import com.volmit.react.Gate;
import com.volmit.react.Surge;
import com.volmit.react.api.ChunkIssue;
import com.volmit.react.api.LagMap;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactScrollEvent;
import com.volmit.react.api.ScrollDirection;
import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;
import com.volmit.volume.lang.collections.GMap;

public class EventController extends Controller
{
	public static LagMap map;
	private GMap<Player, Integer> slots;

	@Override
	public void dump(JSONObject object)
	{
		object.put("lagmap-recordings", map.getChunks().size());
	}

	@Override
	public void start()
	{
		map = new LagMap();
		Surge.register(this);
		slots = new GMap<Player, Integer>();

		for(Player i : Bukkit.getOnlinePlayers())
		{
			slots.put(i, i.getInventory().getHeldItemSlot());
		}
	}

	@Override
	public void stop()
	{
		Surge.unregister(this);
	}

	@Override
	public void tick()
	{
		for(Player i : slots.k())
		{
			int from = slots.get(i);
			int to = i.getInventory().getHeldItemSlot();
			int amt = Math.abs(from - to);
			if(from != to)
			{
				ScrollDirection dir = null;

				if(from < 3 && to > 6)
				{
					dir = ScrollDirection.UP;
					amt = 1;
				}

				else if(to < 3 && from > 6)
				{
					dir = ScrollDirection.DOWN;
					amt = 1;
				}

				else if(from > to)
				{
					dir = ScrollDirection.UP;
				}

				else if(from < to)
				{
					dir = ScrollDirection.DOWN;
				}

				if(dir != null)
				{
					ReactScrollEvent r = new ReactScrollEvent(i, dir, amt);
					Bukkit.getServer().getPluginManager().callEvent(r);

					if(r.isCancelled())
					{
						i.getInventory().setHeldItemSlot(from);
					}

					slots.put(i, i.getInventory().getHeldItemSlot());
				}
			}
		}
	}

	@EventHandler
	public void on(ChunkUnloadEvent e)
	{
		map.getChunks().remove(e.getChunk());
	}

	@EventHandler
	public void on(EntityExplodeEvent e)
	{
		map.hit(e.getLocation().getChunk(), ChunkIssue.TNT, 20);
	}

	@EventHandler
	public void on(BlockPhysicsEvent e)
	{
		try
		{
			map.hit(e.getBlock().getChunk(), ChunkIssue.PHYSICS, 20);
		}

		catch(Throwable ex)
		{

		}
	}

	@EventHandler
	public void on(EntitySpawnEvent e)
	{
		map.hit(e.getLocation().getChunk(), ChunkIssue.ENTITY, 20);
	}

	@EventHandler
	public void on(EntityDeathEvent e)
	{
		map.hit(e.getEntity().getLocation().getChunk(), ChunkIssue.ENTITY, 20);
	}

	@EventHandler
	public void on(EntityDamageEvent e)
	{
		map.hit(e.getEntity().getLocation().getChunk(), ChunkIssue.ENTITY, 20);
	}

	@EventHandler
	public void on(PlayerJoinEvent e)
	{
		if(Gate.safe && Permissable.ACCESS.has(e.getPlayer()) || e.getPlayer().isOp())
		{
			// Gate.msgError(e.getPlayer(), "Warning! You are using a pre-build of spigot
			// 1.13!");
			// Gate.msgError(e.getPlayer(), "React is running in 1.13 safe-mode. Please
			// report any issues to https://github.com/VolmitSoftware/React/issues");
		}

		slots.put(e.getPlayer(), e.getPlayer().getInventory().getHeldItemSlot());
	}

	@EventHandler
	public void on(PlayerQuitEvent e)
	{
		slots.remove(e.getPlayer());
	}

	@Override
	public int getInterval()
	{
		return 1;
	}

	@Override
	public boolean isUrgent()
	{
		return true;
	}
}
