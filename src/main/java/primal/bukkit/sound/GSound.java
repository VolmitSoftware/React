package primal.bukkit.sound;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import primal.bukkit.sound.Audible;

/**
 * A GSound can play a sound
 *
 * @author cyberpwn
 *
 */
public class GSound implements Audible
{
	private String sound;
	private Sound iSound;
	private Float volume;
	private Float pitch;

	/**
	 * Create a sound object
	 *
	 * @param sound
	 *            the string path for the sound
	 * @param volume
	 *            the volume
	 * @param pitch
	 *            the pitch
	 */
	public GSound(String sound, Float volume, Float pitch)
	{
		this.sound = sound;
		this.volume = volume;
		this.pitch = pitch;
	}

	/**
	 * Create a new sound object
	 *
	 * @param iSound
	 *            the string path
	 * @param sound
	 *            the sound path
	 * @param volume
	 *            the volume
	 * @param pitch
	 *            the pitch
	 */
	public GSound(Sound iSound, String sound, Float volume, Float pitch)
	{
		this.iSound = iSound;
		this.sound = sound;
		this.volume = volume;
		this.pitch = pitch;
	}

	/**
	 * Create a sound object
	 *
	 * @param sound
	 *            the sound path
	 */
	public GSound(String sound)
	{
		this.sound = sound;
		this.volume = 1f;
		this.pitch = 1f;
	}

	/**
	 * Create a new sound
	 *
	 * @param iSound
	 * @param volume
	 * @param pitch
	 */
	public GSound(Sound iSound, Float volume, Float pitch)
	{
		this.iSound = iSound;
		this.volume = volume;
		this.pitch = pitch;
	}

	/**
	 * Create a new sound
	 *
	 * @param iSound
	 *            the sound
	 */
	public GSound(Sound iSound)
	{
		this.iSound = iSound;
		this.volume = 1f;
		this.pitch = 1f;
	}

	public void prePlay()
	{

	}

	/**
	 * Play the sound to just one player. No one else can hear it
	 *
	 * @param p
	 *            the player
	 * @param l
	 *            the location
	 */
	@Override
	public void play(Player p, Location l)
	{
		prePlay();

		if(iSound != null)
		{
			p.playSound(l, iSound, volume, pitch);
		}

		if(sound != null)
		{
			String cmd = "playsound " + sound + " " + p.getName() + " " + l.getX() + " " + l.getY() + " " + l.getZ() + " " + volume + " " + pitch;

			p.getServer().dispatchCommand(p.getServer().getConsoleSender(), cmd);
		}
	}

	/**
	 * Play the sound to just one player. No one else can hear it
	 *
	 * @param p
	 *            the player
	 */
	@Override
	public void play(Player p)
	{
		play(p, p.getLocation());
	}

	/**
	 * clone it
	 */
	@Override
	public Audible clone()
	{
		return new GSound(iSound, sound, volume, pitch);
	}

	/**
	 * Play the sound globally to all players
	 *
	 * @param l
	 *            the location
	 */
	@Override
	public void play(Location l)
	{
		prePlay();

		if(iSound != null)
		{
			l.getWorld().playSound(l, iSound, volume, pitch);
		}

		if(sound != null)
		{
			String cmd = "playsound " + sound + " @a " + l.getX() + " " + l.getY() + " " + l.getZ() + " " + volume + " " + pitch;

			Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), cmd);
		}
	}

	/**
	 * Play the sound to just one player. No one else can hear it
	 *
	 * @param p
	 *            the player
	 * @param v
	 *            the vector related to the players location
	 */
	@Override
	public void play(Player p, Vector v)
	{
		prePlay();

		Location l = p.getLocation().clone().add(v);

		if(iSound != null)
		{
			p.playSound(l, iSound, volume, pitch);
		}

		if(sound != null)
		{
			String cmd = "playsound " + sound + " " + p.getName() + " " + l.getX() + " " + l.getY() + " " + l.getZ() + " " + volume + " " + pitch;

			p.getServer().dispatchCommand(p.getServer().getConsoleSender(), cmd);
		}
	}

	/**
	 * Get the sound
	 *
	 * @return the sound path
	 */
	public String getSound()
	{
		return sound;
	}

	/**
	 * Set the sound
	 *
	 * @param sound
	 *            the sound path
	 */
	public void setSound(String sound)
	{
		this.sound = sound;
	}

	/**
	 * Get the object sound
	 *
	 * @return the sound
	 */
	public Sound getiSound()
	{
		return iSound;
	}

	/**
	 * Set the object sound
	 *
	 * @param iSound
	 *            the sound
	 */
	public void setiSound(Sound iSound)
	{
		this.iSound = iSound;
	}

	/**
	 * Get the volume
	 *
	 * @return the volume
	 */
	@Override
	public Float getVolume()
	{
		return volume;
	}

	/**
	 * Set the volume
	 *
	 * @param volume
	 *            the volume
	 */
	@Override
	public void setVolume(Float volume)
	{
		this.volume = volume;
	}

	/**
	 * Get the pitch
	 *
	 * @return the pitch
	 */
	@Override
	public Float getPitch()
	{
		return pitch;
	}

	/**
	 * Set the pitch
	 *
	 * @param pitch
	 *            the pitch
	 */
	@Override
	public void setPitch(Float pitch)
	{
		this.pitch = pitch;
	}
}
