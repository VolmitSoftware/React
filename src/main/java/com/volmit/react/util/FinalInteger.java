package com.volmit.react.util;

/**
 * Represents a number that can be finalized and be changed
 * 
 * @author cyberpwn
 */
public class FinalInteger
{
	private int i;
	
	/**
	 * Create a final number
	 * 
	 * @param i
	 *            the initial number
	 */
	public FinalInteger(int i)
	{
		this.i = i;
	}
	
	/**
	 * Get the value
	 * 
	 * @return the number value
	 */
	public int get()
	{
		return i;
	}
	
	/**
	 * Set the value
	 * 
	 * @param i
	 *            the number value
	 */
	public void set(int i)
	{
		this.i = i;
	}
	
	/**
	 * Add to this value
	 * 
	 * @param i
	 *            the number to add to this value (value = value + i)
	 */
	public void add(int i)
	{
		this.i += i;
	}
	
	/**
	 * Subtract from this value
	 * 
	 * @param i
	 *            the number to subtract from this value (value = value - i)
	 */
	public void sub(int i)
	{
		this.i -= i;
	}
}
