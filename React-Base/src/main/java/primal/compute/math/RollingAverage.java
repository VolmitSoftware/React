package primal.compute.math;

import java.util.Arrays;

/**
 * Represents a rolling average
 *
 * @author cyberpwn
 *
 */
public class RollingAverage
{
	private double[] data;
	private double average;
	private boolean dirty;
	private int currentIndex;

	/**
	 * Create a rolling average
	 *
	 * @param size
	 *            the size
	 */
	public RollingAverage(int size)
	{
		data = new double[size];
	}

	/**
	 * Put data in the average
	 *
	 * @param d
	 *            the double
	 */
	public void put(double d)
	{
		currentIndex = currentIndex >= data.length ? 0 : currentIndex;
		data[currentIndex++] = d;
		dirty = true;
	}

	/**
	 * Get the cached average
	 *
	 * @return the average
	 */
	public double get()
	{
		if(dirty)
		{
			average = computeAverage();
			dirty = false;
		}

		return average;
	}

	private double computeAverage()
	{
		double a = 0;

		for(int i = 0; i < data.length; i++)
		{
			a += data[i];
		}

		return a / data.length;
	}

	/**
	 * Clear the average data
	 *
	 * @param v
	 *            the value to clear with (i.e. 0)
	 */
	public void clear(double v)
	{
		Arrays.fill(data, v);
		average = v;
		dirty = false;
	}

	public int size()
	{
		return data.length;
	}
}
