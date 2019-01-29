package com.volmit.react.api;

import com.volmit.react.util.C;
import com.volmit.react.util.M;

public abstract class Sampler implements ISampler
{
	private double value;
	private String id;
	private String name;
	private String description;
	private C color;
	private C altColor;
	private int interval;

	public Sampler()
	{
		construct();
	}

	@Override
	public String getID()
	{
		return id;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public String getDescription()
	{
		return description;
	}

	@Override
	public C getColor()
	{
		return color;
	}

	@Override
	public C getAltColor()
	{
		return altColor;
	}

	@Override
	public int getInterval()
	{
		return interval;
	}

	@Override
	public abstract void sample();

	@Override
	public abstract void construct();

	@Override
	public abstract String get();

	@Override
	public void setValue(double v)
	{
		value = v;
	}

	@Override
	public double getValue()
	{
		return value;
	}

	@Override
	public void setID(String id)
	{
		this.id = id;
	}

	@Override
	public void setName(String name)
	{
		this.name = name;
	}

	@Override
	public void setDescription(String description)
	{
		this.description = description;
	}

	@Override
	public void setColor(C color, C altColor)
	{
		this.color = color;
		this.altColor = altColor;
	}

	@Override
	public void setInterval(int interval)
	{
		this.interval = (int) M.clip(interval, 1, 1200);
	}
}
