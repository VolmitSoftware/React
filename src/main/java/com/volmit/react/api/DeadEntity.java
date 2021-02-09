package com.volmit.react.api;

import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import com.volmit.react.Config;
import com.volmit.react.util.F;
import com.volmit.react.util.M;
import com.volmit.react.util.ParticleEffect;
import com.volmit.react.util.TICK;

public class DeadEntity
{
	private Entity e;
	private int ticks;
	private int vticks;
	private long ms;
	private long lt;

	public DeadEntity(Entity e, int ticks)
	{
		ms = M.ms();
		this.e = e;
		this.vticks = ticks;
		this.ticks = ticks;
		lt = TICK.tick;
	}

	public boolean c()
	{
		if(e == null || e.isDead())
		{
			return true;
		}

		if(M.ms() - ms > (vticks * 50))
		{
			ticks = 0;
		}

		ticks = vticks - ((int) (TICK.tick - lt));

		if(ticks <= 0)
		{
			if(Config.ENTITY_MARK_PARTICLES)
			{
				ParticleEffect.EXPLOSION_LARGE.display(0.1f, 1, e.getLocation(), 32);
			}

			e.remove();
			e = null;
			return true;
		}

		else
		{
			if(Config.ENTITY_MARK_PARTICLES && M.r(0.05))
			{
				ParticleEffect.VILLAGER_ANGRY.display(0.1f, 1, e.getLocation().clone().add(Vector.getRandom().subtract(Vector.getRandom().clone().multiply(1.89))), 32);
			}

			if(Config.ENTITY_MARK_COUNTDOWN)
			{
				String time = F.time(ticks * 50, 1);
				String n = Config.ENTITY_MARK_COUNTDOWN_FORMAT.replaceAll("%time%", time).replaceAll("\\Q&\\E", "§");

				if(Capability.ENTITY_NAMES.isCapable())
				{
					e.setCustomName(n);
					e.setCustomNameVisible(true);
				}
			}
		}

		return false;
	}

	public Entity getE()
	{
		return e;
	}

	public void setE(Entity e)
	{
		this.e = e;
	}

	public int getTicks()
	{
		return ticks;
	}

	public void setTicks(int ticks)
	{
		this.ticks = ticks;
	}
}
