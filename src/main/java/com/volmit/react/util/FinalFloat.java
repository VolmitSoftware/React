package com.volmit.react.util;

/**
 * Represents a number that can be finalized and be changed
 * 
 * @author cyberpwn
 */
public class FinalFloat
{
	private float i;
	
	/**
	 * Create a final number
	 * 
	 * @param i
	 *            the initial number
	 */
	public FinalFloat(float i)
	{
		this.i = i;
	}
	
	/**
	 * Get the value
	 * 
	 * @return the number value
	 */
	public float get()
	{
		return i;
	}
	
	/**
	 * Set the value
	 * 
	 * @param i
	 *            the number value
	 */
	public void set(float i)
	{
		this.i = i;
	}
	
	/**
	 * Add to this value
	 * 
	 * @param i
	 *            the number to add to this value (value = value + i)
	 */
	public void add(float i)
	{
		this.i += i;
	}
	
	/**
	 * Subtract from this value
	 * 
	 * @param i
	 *            the number to subtract from this value (value = value - i)
	 */
	public void sub(float i)
	{
		this.i -= i;
	}
}
