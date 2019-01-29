package com.volmit.react.util;

import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * Blocks utilities
 *
 * @author cyberpwn
 */
public class Blocks
{
	/**
	 * Get the center of the block
	 *
	 * @param block
	 *            the block
	 * @return the center location
	 */
	public static Location getCenter(Block block)
	{
		return block.getLocation().clone().add(0.5, 0.5, 0.5);
	}
}
