package primal.bukkit.sound;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import primal.bukkit.sched.SR;
import primal.lang.collection.GMap;

/**
 * Represents a basic sequencer
 *
 * @author cyberpwn
 */
public class BasicSequencer implements Sequencer
{
	private float volume;
	private float pitch;
	private GMap<Audible, Integer> table;
	private int playhead;
	private boolean playing;

	public BasicSequencer()
	{
		volume = 1f;
		pitch = 1f;
		table = new GMap<Audible, Integer>();
		playhead = 0;
		playing = false;
	}

	@Override
	public void play(Player p, Location l)
	{
		new SR(0)
		{
			@Override
			public void run()
			{
				if(playhead >= getLength())
				{
					cancel();
					playing = false;
				}

				if(playing)
				{
					for(Audible i : table.k())
					{
						if(table.get(i) == playhead)
						{
							i.play(p, l);
						}
					}

					playhead++;
				}
			}
		};
	}

	@Override
	public void play(Player p)
	{
		new SR(0)
		{
			@Override
			public void run()
			{
				if(playhead >= getLength())
				{
					cancel();
					playing = false;
				}

				if(playing)
				{
					for(Audible i : table.k())
					{
						if(table.get(i) == playhead)
						{
							i.play(p);
						}
					}

					playhead++;
				}
			}
		};
	}

	@Override
	public void play(Location l)
	{
		new SR(0)
		{
			@Override
			public void run()
			{
				if(playhead >= getLength())
				{
					cancel();
					playing = false;
				}

				if(playing)
				{
					for(Audible i : table.k())
					{
						if(table.get(i) == playhead)
						{
							i.play(l);
						}
					}

					playhead++;
				}
			}
		};
	}

	@Override
	public void play(Player p, Vector v)
	{
		new SR(0)
		{
			@Override
			public void run()
			{
				if(playhead >= getLength())
				{
					cancel();
					playing = false;
				}

				if(playing)
				{
					for(Audible i : table.k())
					{
						if(table.get(i) == playhead)
						{
							i.play(p, v);
						}
					}

					playhead++;
				}
			}
		};
	}

	@Override
	public Float getVolume()
	{
		return volume;
	}

	@Override
	public void setVolume(Float volume)
	{
		this.volume = volume;
	}

	@Override
	public Float getPitch()
	{
		return pitch;
	}

	@Override
	public void setPitch(Float pitch)
	{
		this.pitch = pitch;
	}

	@Override
	public Audible clone()
	{
		return null;
	}

	@Override
	public void add(Audible a, int timing)
	{
		table.put(a, timing);
	}

	@Override
	public boolean isPlaying()
	{
		return playing;
	}

	@Override
	public void setPlayHead(int p)
	{
		playhead = p;
	}

	@Override
	public int getLength()
	{
		int max = 0;

		for(Audible i : table.k())
		{
			if(table.get(i) > max)
			{
				max = table.get(i);
			}
		}

		return max;
	}

	@Override
	public int getPlayHead()
	{
		return playhead;
	}
}
