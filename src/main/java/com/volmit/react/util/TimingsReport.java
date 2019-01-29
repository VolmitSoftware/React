package com.volmit.react.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.volmit.react.Gate;
import com.volmit.react.api.SampledType;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;

public class TimingsReport
{
	private GMap<SampledType, Double> data;
	private double samples;
	private Task task;
	private GList<Player> spam;

	public TimingsReport()
	{
		data = new GMap<SampledType, Double>();
		samples = 1;
		spam = new GList<Player>();
	}

	public void reportTo(CommandSender s)
	{
		s.sendMessage(Gate.header(samples + " Samples", C.YELLOW));

		for(SampledType i : data.k())
		{
			if(data.get(i) == 0)
			{
				continue;
			}

			s.sendMessage(i.get().getColor() + i.get().getName() + ": " + C.WHITE + i.get().getFormatter().from(data.get(i)));
		}

		s.sendMessage(C.YELLOW + "This data reflects averages, it does not reveal spikes.");

		s.sendMessage(Gate.header(C.YELLOW));
	}

	public void spam(Player p)
	{
		if(spam.contains(p))
		{
			spam.remove(p);
		}

		else
		{
			spam.add(p);
		}
	}

	public void start()
	{
		samples = 1;
		data.clear();

		task = new Task("timings-samp", 0)
		{
			@Override
			public void run()
			{
				sample();

				if(TICK.tick % 5 == 0)
				{
					for(Player i : spam)
					{
						reportTo(i);
					}
				}
			}
		};
	}

	public void stop()
	{
		task.cancel();
	}

	private void sample()
	{
		samples++;
		sample(SampledType.TICK);
		sample(SampledType.TILE_TIME);
		sample(SampledType.ENTITY_TIME);
		sample(SampledType.EXPLOSION_TIME);
		sample(SampledType.FLUID_TIME);
		sample(SampledType.GROWTH_TIME);
		sample(SampledType.HOPPER_TIME);
		sample(SampledType.PHYSICS_TIME);
		sample(SampledType.REDSTONE_TIME);
	}

	private void sample(SampledType t)
	{
		if(!data.containsKey(t))
		{
			data.put(t, 0.0);
		}

		data.put(t, ((data.get(t) * (samples - 1)) + t.get().getValue()) / samples);
	}
}
