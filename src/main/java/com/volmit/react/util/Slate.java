package com.volmit.react.util;

import org.bukkit.entity.Player;

import primal.lang.collection.GList;

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
    void build();

	/**
	 * Set the name of the slate
	 *
	 * @param name
	 *            the name of the slate
	 */
    void setName(String name);

	/**
	 * Get the name of the slate
	 *
	 * @return the name
	 */
    String getName();

	/**
	 * Get all lines in the board
	 *
	 * @return the lines
	 */
    GList<String> getLines();

	/**
	 * Set a line in the board
	 *
	 * @param index
	 *            the index
	 * @param line
	 *            the line text
	 */
    void set(int index, String line);

	/**
	 * Set the board lines
	 *
	 * @param lines
	 *            the lines
	 */
    void setLines(GList<String> lines);

	/**
	 * Get a line in the board
	 *
	 * @param index
	 *            the index
	 * @return the line or null
	 */
    String get(int index);

	/**
	 * Add a player viewer
	 *
	 * @param p
	 *            the player
	 */
    void addViewer(Player p);

	/**
	 * Add a line of text
	 *
	 * @param s
	 *            the line
	 */
    void addLine(String s);

	/**
	 * Clear all lines
	 */
    void clearLines();

	/**
	 * Remove a player viewer
	 *
	 * @param p
	 *            the player
	 */
    void removeViewer(Player p);

	/**
	 * Get all viewers
	 *
	 * @return viewers of this slate
	 */
    GList<Player> getViewers();

	/**
	 * Is the given player viewing this slate
	 *
	 * @param p
	 *            the player
	 * @return true if the player is
	 */
    boolean isViewing(Player p);

	/**
	 * Update the board to all players online
	 */
    void update();
}