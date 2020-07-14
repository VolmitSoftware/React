package primal.bukkit.sound;

/**
 * Sequencing of audio
 * 
 * @author cyberpwn
 */
public interface Sequencer extends Audible
{
	/**
	 * Add an audible object to the track with a time slot (in ticks)
	 * 
	 * @param a
	 *            the audible
	 * @param timing
	 *            the amount of ticks into the sequence
	 */
	public void add(Audible a, int timing);
	
	/**
	 * Is the sequencer currently playing?
	 * 
	 * @return true if it is
	 */
	public boolean isPlaying();
	
	/**
	 * Set the position of the playhead
	 * 
	 * @param p
	 *            the positition
	 */
	public void setPlayHead(int p);
	
	/**
	 * Get the length of this sequence in ticks
	 * 
	 * @return the length or 0
	 */
	public int getLength();
	
	/**
	 * Get the position of the playhead
	 * 
	 * @return the playhead position
	 */
	public int getPlayHead();
}
