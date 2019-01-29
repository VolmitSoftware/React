package com.volmit.react.util;

/**
 * Represents a range between two numbers
 * 
 * @author cyberpwn
 */
public class Range
{
	private double min;
	private double max;
	
	/**
	 * Create a double range
	 * 
	 * @param min
	 *            the min
	 * @param max
	 *            the max
	 */
	public Range(double min, double max)
	{
		this.min = min;
		this.max = max;
	}
	
	/**
	 * Create an int range
	 * 
	 * @param min
	 *            the min
	 * @param max
	 *            the max
	 */
	public Range(int min, int max)
	{
		this((double) min, (double) max);
	}
	
	/**
	 * Create a long range
	 * 
	 * @param min
	 *            the min
	 * @param max
	 *            the max
	 */
	public Range(long min, long max)
	{
		this((double) min, (double) max);
	}
	
	/**
	 * Get a random next double
	 * 
	 * @return the next double
	 */
	public double randomNext()
	{
		return min + (Math.random() * ((max - min) + 1));
	}
	
	/**
	 * Get a random next int
	 * 
	 * @return the next int
	 */
	public int randomNextInt()
	{
		return (int) randomNext();
	}
	
	/**
	 * Get a random next long
	 * 
	 * @return the next long
	 */
	public long randomNextLong()
	{
		return (long) randomNext();
	}
	
	/**
	 * Is the given number within the range INCLUSIVE?
	 * 
	 * @param n
	 *            the number
	 * @return returns true if the number is at or within the range
	 */
	public boolean isWithin(double n)
	{
		return n >= min && n <= max;
	}
	
	/**
	 * Is the given number within the range INCLUSIVE?
	 * 
	 * @param n
	 *            the number
	 * @return returns true if the number is at or within the range
	 */
	public boolean isWithin(int n)
	{
		return isWithin((double) n);
	}
	
	/**
	 * Is the given number within the range INCLUSIVE?
	 * 
	 * @param n
	 *            the number
	 * @return returns true if the number is at or within the range
	 */
	public boolean isWithin(long n)
	{
		return isWithin((double) n);
	}
	
	public double getMin()
	{
		return min;
	}
	
	public void setMin(double min)
	{
		this.min = min;
	}
	
	public double getMax()
	{
		return max;
	}
	
	public void setMax(double max)
	{
		this.max = max;
	}
}
