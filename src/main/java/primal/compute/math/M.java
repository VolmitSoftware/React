package primal.compute.math;


import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Math
 *
 * @author cyberpwn
 */
public class M
{
	private static final int precision = 128;
	private static final int modulus = 360 * precision;
	private static final float[] sin = new float[modulus];

	public static double clip(double value, double min, double max)
	{
		return Math.min(max, Math.max(min, value));
	}

	/**
	 * Get true or false based on random percent
	 *
	 * @param d
	 *            between 0 and 1
	 * @return true if true
	 */
	public static boolean r(Double d)
	{
		if(d == null)
		{
			return Math.random() < 0.5;
		}

		return Math.random() < d;
	}

	/**
	 * Get the ticks per second from a time in nanoseconds, the rad can be used for
	 * multiple ticks
	 *
	 * @param ns
	 *            the time in nanoseconds
	 * @param rad
	 *            the radius of the time
	 * @return the ticks per second in double form
	 */
	public static double tps(long ns, int rad)
	{
		return (20.0 * (ns / 50000000.0)) / rad;
	}

	/**
	 * Get the number of ticks from a time in nanoseconds
	 *
	 * @param ns
	 *            the nanoseconds
	 * @return the amount of ticks
	 */
	public static double ticksFromNS(long ns)
	{
		return (ns / 50000000.0);
	}

	/**
	 * Get roman numeral representation of the int
	 *
	 * @param num
	 *            the int
	 * @return the numerals
	 */
	public static String toRoman(int num)
	{
		LinkedHashMap<String, Integer> roman_numerals = new LinkedHashMap<String, Integer>();

		roman_numerals.put("M", 1000);
		roman_numerals.put("CM", 900);
		roman_numerals.put("D", 500);
		roman_numerals.put("CD", 400);
		roman_numerals.put("C", 100);
		roman_numerals.put("XC", 90);
		roman_numerals.put("L", 50);
		roman_numerals.put("XL", 40);
		roman_numerals.put("X", 10);
		roman_numerals.put("IX", 9);
		roman_numerals.put("V", 5);
		roman_numerals.put("IV", 4);
		roman_numerals.put("I", 1);

		String res = "";

		for(Map.Entry<String, Integer> entry : roman_numerals.entrySet())
		{
			int matches = num / entry.getValue();

			res += repeat(entry.getKey(), matches);
			num = num % entry.getValue();
		}

		return res;
	}

	/**
	 * Repeat a string
	 *
	 * @param s
	 *            the string
	 * @param n
	 *            the amount of times to repeat
	 * @return the repeated string
	 */
	private static String repeat(String s, int n)
	{
		if(s == null)
		{
			return null;
		}

		final StringBuilder sb = new StringBuilder();

		for(int i = 0; i < n; i++)
		{
			sb.append(s);
		}

		return sb.toString();
	}

	public static int rand(int f, int t)
	{
		return f + (int) (Math.random() * ((t - f) + 1));
	}

	/**
	 * Get the number representation from roman numerals.
	 *
	 * @param number
	 *            the roman number
	 * @return the int representation
	 */
	public static int fromRoman(String number)
	{
		if(number.isEmpty())
		{
			return 0;
		}

		number = number.toUpperCase();

		if(number.startsWith("M"))
		{
			return 1000 + fromRoman(number.substring(1));
		}

		if(number.startsWith("CM"))
		{
			return 900 + fromRoman(number.substring(2));
		}

		if(number.startsWith("D"))
		{
			return 500 + fromRoman(number.substring(1));
		}

		if(number.startsWith("CD"))
		{
			return 400 + fromRoman(number.substring(2));
		}

		if(number.startsWith("C"))
		{
			return 100 + fromRoman(number.substring(1));
		}

		if(number.startsWith("XC"))
		{
			return 90 + fromRoman(number.substring(2));
		}

		if(number.startsWith("L"))
		{
			return 50 + fromRoman(number.substring(1));
		}

		if(number.startsWith("XL"))
		{
			return 40 + fromRoman(number.substring(2));
		}

		if(number.startsWith("X"))
		{
			return 10 + fromRoman(number.substring(1));
		}

		if(number.startsWith("IX"))
		{
			return 9 + fromRoman(number.substring(2));
		}

		if(number.startsWith("V"))
		{
			return 5 + fromRoman(number.substring(1));
		}

		if(number.startsWith("IV"))
		{
			return 4 + fromRoman(number.substring(2));
		}

		if(number.startsWith("I"))
		{
			return 1 + fromRoman(number.substring(1));
		}

		return 0;
	}

	/**
	 * Get system Nanoseconds
	 *
	 * @return nanoseconds (current)
	 */
	public static long ns()
	{
		return System.nanoTime();
	}

	/**
	 * Get the current millisecond time
	 *
	 * @return milliseconds
	 */
	public static long ms()
	{
		return System.currentTimeMillis();
	}

	/**
	 * Fast sin function
	 *
	 * @param a
	 *            the number
	 * @return the sin
	 */
	public static float sin(float a)
	{
		return sinLookup((int) (a * precision + 0.5f));
	}

	/**
	 * Fast cos function
	 *
	 * @param a
	 *            the number
	 * @return the cos
	 */
	public static float cos(float a)
	{
		return sinLookup((int) ((a + 90f) * precision + 0.5f));
	}

	/**
	 * Biggest number
	 *
	 * @param ints
	 *            the numbers
	 * @return the biggest one
	 */
	public static int max(int... ints)
	{
		int max = Integer.MIN_VALUE;

		for(int i : ints)
		{
			if(i > max)
			{
				max = i;
			}
		}

		return max;
	}

	/**
	 * Smallest number
	 *
	 * @param ints
	 *            the numbers
	 * @return the smallest one
	 */
	public static int min(int... ints)
	{
		int min = Integer.MAX_VALUE;

		for(int i : ints)
		{
			if(i < min)
			{
				min = i;
			}
		}

		return min;
	}

	/**
	 * is the number "is" within from-to
	 *
	 * @param from
	 *            the lower end
	 * @param to
	 *            the upper end
	 * @param is
	 *            the check
	 * @return true if its within
	 */
	public static boolean within(int from, int to, int is)
	{
		return is >= from && is <= to;
	}

	static
	{
		for(int i = 0; i < sin.length; i++)
		{
			sin[i] = (float) Math.sin((i * Math.PI) / (precision * 180));
		}
	}

	private static float sinLookup(int a)
	{
		return a >= 0 ? sin[a % (modulus)] : -sin[-a % (modulus)];
	}

	public static double absoluteZero(double d)
	{
		return d < 0 ? 0 : d;
	}
}
