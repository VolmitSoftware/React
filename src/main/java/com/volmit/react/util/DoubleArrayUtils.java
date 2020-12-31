package com.volmit.react.util;

import java.util.Arrays;

public class DoubleArrayUtils
{
	public static void shiftRight(double[] values, double push)
	{
		if (values.length - 2 + 1 >= 0) System.arraycopy(values, 0, values, 1, values.length - 2 + 1);

		values[0] = push;
	}

	public static void wrapRight(double[] values)
	{
		double last = values[values.length - 1];
		shiftRight(values, last);
	}

	public static void fill(double[] values, double value)
	{
		Arrays.fill(values, value);
	}
}
