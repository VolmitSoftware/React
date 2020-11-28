package primal.bukkit.nms;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class PacketBuffer
{
	private final List<Object> packets;

	public PacketBuffer()
	{
		packets = new ArrayList<>();
	}

	public PacketBuffer q(Object o)
	{
		packets.add(o);
		return this;
	}

	public PacketBuffer q(List<Object> o)
	{
		for(Object i : o)
		{
			q(i);
		}

		return this;
	}

	@SuppressWarnings("deprecation")
	public PacketBuffer flush(Player p, Plugin plugin, int perInterval, int interval, Runnable onDone)
	{
		int[] x = new int[] {-1};
		x[0] = Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, new Runnable()
		{
			@Override
			public void run()
			{
				for(int i = 0; i < perInterval; i++)
				{
					if(packets.isEmpty())
					{
						break;
					}

					Object o = packets.get(0);
					packets.remove(0);
					NMP.host.sendPacket(p, o);
				}

				if(packets.isEmpty())
				{
					Bukkit.getScheduler().cancelTask(x[0]);
					onDone.run();
				}
			}
		}, 0, interval);

		return this;
	}

	public PacketBuffer flush(Player p)
	{
		for(Object i : packets)
		{
			NMP.host.sendPacket(p, i);
		}

		return this;
	}
}
