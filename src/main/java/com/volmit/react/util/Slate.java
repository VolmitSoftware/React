package com.volmit.react.util;

import org.bukkit.entity.Player;

import com.volmit.volume.lang.collections.GList;

/**
 * Slate scoreboard interface
 *
 * @author cyberpwn
 */
public interface Slate
{
	/**
	 * Rebuild the scoreboard
	 */
	public void build();

	/**
	 * Set the name of the slate
	 *
	 * @param name
	 *            the name of the slate
	 */
	public void setName(String name);

	/**
	 * Get the name of the slate
	 *
	 * @return the name
	 */
	public String getName();

	/**
	 * Get all lines in the board
	 *
	 * @return the lines
	 */
	public GList<String> getLines();

	/**
	 * Set a line in the board
	 *
	 * @param index
	 *            the index
	 * @param line
	 *            the line text
	 */
	public void set(int index, String line);

	/**
	 * Set the board lines
	 *
	 * @param lines
	 *            the lines
	 */
	public void setLines(GList<String> lines);

	/**
	 * Get a line in the board
	 *
	 * @param index
	 *            the index
	 * @return the line or null
	 */
	public String get(int index);

	/**
	 * Add a player viewer
	 *
	 * @param p
	 *            the player
	 */
	public void addViewer(Player p);

	/**
	 * Add a line of text
	 *
	 * @param s
	 *            the line
	 */
	public void addLine(String s);

	/**
	 * Clear all lines
	 */
	public void clearLines();

	/**
	 * Remove a player viewer
	 *
	 * @param p
	 *            the player
	 */
	public void removeViewer(Player p);

	/**
	 * Get all viewers
	 *
	 * @return viewers of this slate
	 */
	public GList<Player> getViewers();

	/**
	 * Is the given player viewing this slate
	 *
	 * @param p
	 *            the player
	 * @return true if the player is
	 */
	public boolean isViewing(Player p);

	/**
	 * Update the board to all players online
	 */
	public void update();
}