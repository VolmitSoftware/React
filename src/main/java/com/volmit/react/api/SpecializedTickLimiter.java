package com.volmit.react.api;

import org.spigotmc.TickLimiter;

import com.volmit.react.Config;
import com.volmit.react.util.Average;
import com.volmit.react.util.M;

public class SpecializedTickLimiter extends TickLimiter // heh
{
	public double rMaxTime;
	public long rStartTime;
	public double rLastTime;
	public long rMark;
	public double tMaxTime;
	public Average atimes = new Average(5);
	public Average adropped = new Average(20);
	private int droppedTicks;
	private boolean entityTick;

	public SpecializedTickLimiter(double maxtime, boolean entityTick)
	{
		super((int) maxtime);

		this.rMark = M.ns();
		this.rMaxTime = maxtime;
		this.tMaxTime = maxtime;
		this.droppedTicks = 0;
		this.entityTick = entityTick;
	}

	@Override
	public void initTick()
	{
		rLastTime = (double) (rMark - rStartTime) / 1000000.0;
		this.rStartTime = M.ns();
		atimes.put(M.clip(rLastTime, 0, 1000));
		adropped.put(droppedTicks);

		if((entityTick && Config.SMEAR_TICK_ENTITIES_ENABLE) || (!entityTick && Config.SMEAR_TICK_TILES_ENABLE))
		{
			double k = atimes.getAverage();

			if(droppedTicks > 0)
			{
				k += 0.25;
			}

			tMaxTime = M.clip(k, 0.15, 50) + (entityTick ? Config.SMEAR_TICK_ENTITIES_SEPERATION_BIAS : Config.SMEAR_TICK_TILES_SEPERATION_BIAS);

			if(Math.abs(tMaxTime - rMaxTime) > 0.01)
			{
				double d = Math.abs(tMaxTime - rMaxTime) / (entityTick ? Config.SMEAR_TICK_ENTITIES_AMOUNT : Config.SMEAR_TICK_TILES_AMOUNT);

				if(tMaxTime > rMaxTime)
				{
					rMaxTime += d;
				}

				else
				{
					rMaxTime -= d;
				}
			}
		}

		else
		{
			rMaxTime = 50;
			tMaxTime = 50;
		}

		droppedTicks = 0;
	}

	@Override
	public boolean shouldContinue()
	{
		long remaining = M.ns() - this.rStartTime;
		boolean con = remaining < (long) (this.rMaxTime * 1000000.0);

		if(con)
		{
			rMark = M.ns();
		}

		else
		{
			droppedTicks++;
		}

		return con;
	}
}
