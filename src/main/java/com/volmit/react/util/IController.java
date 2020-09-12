package com.volmit.react.util;

import org.json.JSONObject;

public interface IController
{
	void start();

	void stop();

	void tick();

	int getInterval();

	boolean isUrgent();

	void setTime(double ms);

	double getTime();

	void dump(JSONObject object);
}
