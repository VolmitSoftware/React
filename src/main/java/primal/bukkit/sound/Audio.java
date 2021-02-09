package primal.bukkit.sound;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import primal.compute.math.Average;
import primal.lang.collection.GList;

/**
 * A collection of audibles which may contain more sub-audibles or actual sound
 * objects. This allows you to create sounds with mixtures of multiple sounds
 * and volumes
 *
 * @author cyberpwn
 */
public class Audio implements Audible
{
	private GList<Audible> audibles;

	/**
	 * Create an audible object
	 */
	public Audio()
	{
		audibles = new GList<Audible>();
	}

	/**
	 * Create an audible entity with multiple sub-audibles
	 *
	 * @param audibles
	 *            the audibles
	 */
	public Audio(GList<Audible> audibles)
	{
		this.audibles = audibles;
	}

	@Override
	public Audible clone()
	{
		return new Audio(audibles);
	}

	/**
	 * Add an audible object to the sound entity
	 *
	 * @param audible
	 *            to be played with the others
	 */
	public void add(Audible audible)
	{
		audibles.add(audible);
	}

	public Audio qadd(Audible audible)
	{
		audibles.add(audible);
		return this;
	}

	@Override
	public void play(Player p, Location l)
	{
		for(Audible i : audibles)
		{
			i.play(p, l);
		}
	}

	@Override
	public void play(Player p)
	{
		for(Audible i : audibles)
		{
			i.play(p);
		}
	}

	@Override
	public void play(Location l)
	{
		for(Audible i : audibles)
		{
			i.play(l);
		}
	}

	@Override
	public void play(Player p, Vector v)
	{
		for(Audible i : audibles)
		{
			i.play(p, v);
		}
	}

	@Override
	public Float getVolume()
	{
		Average a = new Average(-1);

		for(Audible i : audibles)
		{
			a.put(i.getVolume());
		}

		return (float) a.getAverage();
	}

	@Override
	public void setVolume(Float volume)
	{
		for(Audible i : audibles)
		{
			i.setVolume(volume);
		}
	}

	@Override
	public Float getPitch()
	{
		Average a = new Average(-1);

		for(Audible i : audibles)
		{
			a.put(i.getPitch());
		}

		return (float) a.getAverage();
	}

	@Override
	public void setPitch(Float pitch)
	{
		for(Audible i : audibles)
		{
			i.setPitch(pitch);
		}
	}
}
