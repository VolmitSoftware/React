package com.volmit.react.controller;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Arrow.PickupStatus;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import com.volmit.react.Config;
import com.volmit.react.Surge;
import com.volmit.react.api.Capability;
import com.volmit.react.util.Area;
import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;
import com.volmit.react.util.S;
import com.volmit.react.util.TICK;
import com.volmit.volume.lang.collections.GList;

public class InstantDropController extends Controller
{
	private GList<Integer> ignore;

	@Override
	public void dump(JSONObject object)
	{
		object.put("ignored", ignore.size());
	}

	@Override
	public void start()
	{
		Surge.register(this);
		ignore = new GList<Integer>();
	}

	@Override
	public void stop()
	{
		Surge.unregister(this);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(BlockBreakEvent e)
	{
		if(!Config.SKIP_ORBS)
		{
			return;
		}

		if(!Config.getWorldConfig(e.getBlock().getWorld()).allowXPHandling)
		{
			return;
		}

		if(e.getPlayer() != null)
		{
			if(e.getExpToDrop() > 0)
			{
				e.getPlayer().giveExp(e.getExpToDrop());
				e.setExpToDrop(0);
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(PlayerDropItemEvent e)
	{
		if(!Config.DROPS_INSTADROP)
		{
			return;
		}

		if(!Config.getWorldConfig(e.getPlayer().getWorld()).allowDropHandling)
		{
			return;
		}

		ignore.add(e.getItemDrop().getEntityId());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(EntityDeathEvent e)
	{
		if(!Config.SKIP_ORBS)
		{
			return;
		}

		if(!Config.getWorldConfig(e.getEntity().getWorld()).allowXPHandling)
		{
			return;
		}

		if(e.getEntity().getKiller() != null)
		{
			e.getEntity().getKiller().giveExp(e.getDroppedExp());
			e.setDroppedExp(0);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(PlayerMoveEvent e)
	{
		if(!Config.FAST_ORB_PICKUP)
		{
			return;
		}

		if(!Config.getWorldConfig(e.getPlayer().getWorld()).allowXPHandling)
		{
			return;
		}

		if(TICK.tick % 14 == 0)
		{
			Area a = new Area(e.getPlayer().getLocation(), 2.3);

			for(Entity i : a.getNearbyEntities())
			{
				if(i instanceof ExperienceOrb)
				{
					e.getPlayer().giveExp(((ExperienceOrb) i).getExperience());
					i.remove();
				}
			}
		}
	}

	@EventHandler
	public void on(ProjectileHitEvent e)
	{
		if(!Config.DESPAWN_USELESS_ARROWS)
		{
			return;
		}

		if(!Capability.ARROW_OWNER.isCapable())
		{
			return;
		}

		if(!Config.getWorldConfig(e.getEntity().getWorld()).allowDropHandling)
		{
			return;
		}

		if(e.getEntityType().equals(EntityType.ARROW))
		{
			Arrow a = (Arrow) e.getEntity();

			new S("arrow.remove-check")
			{
				@Override
				public void run()
				{
					if(a.isOnGround() && !a.getPickupStatus().equals(PickupStatus.ALLOWED))
					{
						a.remove();
					}
				}
			};
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(ItemSpawnEvent e)
	{
		if(!Config.DROPS_INSTADROP)
		{
			return;
		}

		if(!Config.getWorldConfig(e.getEntity().getWorld()).allowDropHandling)
		{
			return;
		}

		if(ignore.contains(e.getEntity().getEntityId()))
		{
			ignore.remove((Integer) e.getEntity().getEntityId());
			return;
		}

		double dd = Double.MAX_VALUE;
		Player p = null;

		for(Player i : new Area(e.getEntity().getLocation(), 5.5).getNearbyPlayers())
		{
			double dv = i.getLocation().distanceSquared(e.getEntity().getLocation());
			if(dv < dd)
			{
				dd = dv;
				p = i;
			}
		}

		Player f = p;
		if(p != null)
		{
			e.getEntity().setPickupDelay(0);

			if(Config.DROPS_TELEPORT)
			{
				new S("tp-entity-drop")
				{
					@Override
					public void run()
					{
						e.getEntity().teleport(f.getLocation());
					}
				};
			}
		}
	}

	@Override
	public void tick()
	{

	}

	@Override
	public int getInterval()
	{
		return 1115;
	}

	@Override
	public boolean isUrgent()
	{
		return false;
	}
}
