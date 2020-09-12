package com.volmit.react.api;

import primal.util.text.C;

public interface ISampler
{
	String getID();

	IFormatter getFormatter();

	String getName();

	String getDescription();

	void setID(String id);

	void setName(String name);

	void setDescription(String description);

	C getColor();

	C getAltColor();

	void setColor(C color, C alt);

	int getInterval();

	void setInterval(int interval);

	void sample();

	String get();

	void construct();

	void setValue(double v);

	double getValue();
}
