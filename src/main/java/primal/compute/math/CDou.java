package primal.compute.math;

public class CDou
{
	private double number;
	private final double max;

	public CDou(double max)
	{
		number = 0;
		this.max = max;
	}

	public CDou set(double n)
	{
		number = n;
		circ();
		return this;
	}

	public CDou add(double a)
	{
		number += a;
		circ();
		return this;
	}

	public CDou sub(double a)
	{
		number -= a;
		circ();
		return this;
	}

	public double get()
	{
		return number;
	}

	public void circ()
	{
		if(number < 0)
		{
			number = max - (Math.min(Math.abs(number), max));
		}

		number = number % (max);
	}
}