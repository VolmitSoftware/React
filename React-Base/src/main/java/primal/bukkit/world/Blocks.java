package primal.bukkit.world;

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

	/**
	 * Update the block physics
	 *
	 * @param block
	 *            the block
	 */
	public static void update(Block block)
	{
		// TODO MISSING
	}

	/**
	 * Update the block and the facing blocks (7 updates)
	 *
	 * @param block
	 *            the block
	 */
	public static void updateAround(Block block)
	{
		for(Block i : W.blockFaces(block))
		{
			update(i);
		}

		update(block);
	}
}