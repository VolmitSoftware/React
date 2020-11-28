package primal.bukkit.world;

/**
 * Represents a snow level
 *
 * @author cyberpwn
 */
public class SnowLevel
{
	private byte level;

	/**
	 * Create redstone power
	 *
	 * @param power
	 *            the power level from 0-15
	 */
	public SnowLevel(byte power)
	{
		this.level = power;
	}

	/**
	 * Get the level of snow
	 *
	 * @return the snow level from 0-7
	 */
	public byte getLevel()
	{
		if(level > 7)
		{
			return 7;
		}

		if(level < 0)
		{
			return 0;
		}

		return level;
	}

	/**
	 * Set the snow level
	 *
	 * @param level
	 *            the given level between 0 and 7
	 */
	public void setLevel(byte level)
	{
		this.level = level;
	}
}