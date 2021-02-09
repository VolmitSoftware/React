package com.volmit.react.util;

/**
 * Health utility
 * 
 * @author cyberpwn
 *
 */
public class Health
{
	private double hp;
	
	/**
	 * Create hp
	 * 
	 * @param hp
	 *            hp
	 */
	public Health(double hp)
	{
		this.hp = hp;
	}
	
	/**
	 * Get a flat integer of hearts. This is rounded.
	 * 
	 * @return the hearts in ints
	 */
	public int getFlatHearts()
	{
		return (int) (hp / 2);
	}
	
	/**
	 * Get hearts in double form. Not rounded
	 * 
	 * @return the hearts
	 */
	public double getHearts()
	{
		return (hp / 2);
	}
	
	/**
	 * Get half hearts in double form. Not rounded
	 * 
	 * @return hearts
	 */
	public double getHalfHearts()
	{
		return (hp);
	}
	
	/**
	 * Get a flat integer of half hearts. This is rounded.
	 * 
	 * @return the half hearts in ints
	 */
	public int getFlatHalfHearts()
	{
		return (int) (hp);
	}
}
