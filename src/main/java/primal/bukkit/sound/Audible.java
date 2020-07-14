package primal.bukkit.sound;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Allows you to wrap either objects or more folders of wrappers into one
 * audible object allowing multiple sounds at different pitches and volumes be
 * played with one object
 * 
 * @author cyberpwn
 *
 */
public interface Audible
{
	/**
	 * Play the sound to just the player
	 * 
	 * @param p
	 *            the player
	 * @param l
	 *            the location
	 */
	public void play(Player p, Location l);
	
	/**
	 * Play the sound to just the player
	 * 
	 * @param p
	 *            the player
	 */
	public void play(Player p);
	
	/**
	 * Play the sound globally
	 * 
	 * @param l
	 *            the location
	 */
	public void play(Location l);
	
	/**
	 * Play the sound to the player
	 * 
	 * @param p
	 *            the player
	 * @param v
	 *            relative to the players location
	 */
	public void play(Player p, Vector v);
	
	/**
	 * Get volume
	 * 
	 * @return the volume
	 */
	public Float getVolume();
	
	/**
	 * Sets the volume
	 * 
	 * @param volume
	 *            the volume
	 */
	public void setVolume(Float volume);
	
	/**
	 * get the pitch
	 * 
	 * @return the pitch
	 */
	public Float getPitch();
	
	/**
	 * Set the pitch
	 * 
	 * @param pitch
	 *            the pitch
	 */
	public void setPitch(Float pitch);
	
	/**
	 * Clone the audio
	 * 
	 * @return the audio
	 */
	public Audible clone();
}
