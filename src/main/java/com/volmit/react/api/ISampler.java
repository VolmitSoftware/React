package com.volmit.react.api;

import com.volmit.react.util.C;

public interface ISampler
{
	public String getID();

	public IFormatter getFormatter();

	public String getName();

	public String getDescription();

	public void setID(String id);

	public void setName(String name);

	public void setDescription(String description);

	public C getColor();

	public C getAltColor();

	public void setColor(C color, C alt);

	public int getInterval();

	public void setInterval(int interval);

	public void sample();

	public String get();

	public void construct();

	public void setValue(double v);

	public double getValue();
}
