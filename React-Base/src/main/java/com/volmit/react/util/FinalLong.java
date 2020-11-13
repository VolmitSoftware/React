package com.volmit.react.util;

/**
 * Represents a number that can be finalized and be changed
 * 
 * @author cyberpwn
 */
public class FinalLong
{
	private long i;
	
	/**
	 * Create a final number
	 * 
	 * @param i
	 *            the initial number
	 */
	public FinalLong(long i)
	{
		this.i = i;
	}
	
	/**
	 * Get the value
	 * 
	 * @return the number value
	 */
	public long get()
	{
		return i;
	}
	
	/**
	 * Set the value
	 * 
	 * @param i
	 *            the number value
	 */
	public void set(long i)
	{
		this.i = i;
	}
	
	/**
	 * Add to this value
	 * 
	 * @param i
	 *            the number to add to this value (value = value + i)
	 */
	public void add(long i)
	{
		this.i += i;
	}
	
	/**
	 * Subtract from this value
	 * 
	 * @param i
	 *            the number to subtract from this value (value = value - i)
	 */
	public void sub(long i)
	{
		this.i -= i;
	}
}
