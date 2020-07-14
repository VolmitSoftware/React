package primal.compute.math;

public class CNum
{
	private int number;
	private int max;

	public int getMax()
	{
		return max;
	}

	public CNum(int max)
	{
		number = 0;
		this.max = max;
	}

	public CNum set(int n)
	{
		number = n;
		circ();
		return this;
	}

	public CNum add(int a)
	{
		number += a;
		circ();
		return this;
	}

	public CNum sub(int a)
	{
		number -= a;
		circ();
		return this;
	}

	public int get()
	{
		return number;
	}

	public void circ()
	{
		if(number < 0)
		{
			number = max - (Math.abs(number) > max ? max : Math.abs(number));
		}

		number = number % (max);
	}
}