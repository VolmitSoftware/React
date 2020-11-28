package com.volmit.react.util;

public class Average
{
	private double[] values;
	private double average;
	private boolean dirty;

	public Average(int size)
	{
		values = new double[size];
		DoubleArrayUtils.fill(values, 0);
		average = 0;
		dirty = false;
	}

	public void put(double i)
	{
		DoubleArrayUtils.shiftRight(values, i);
		dirty = true;
	}

	public double getAverage()
	{
		if(dirty)
		{
			calculateAverage();
			return getAverage();
		}

		return average;
	}

	public int size()
	{
		return values.length;
	}

	private void calculateAverage()
	{
		double d = 0;

		for(double i : values)
		{
			d += i;
		}

		average = d / (double) values.length;
		dirty = false;
	}
}
