package com.volmit.react.controller;

import com.volmit.react.Surge;
import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;

public class TickListController extends Controller
{
	@Override
	public void dump(JSONObject object)
	{

	}

	@Override
	public void start()
	{
		Surge.register(this);

	}

	@Override
	public void stop()
	{
		Surge.unregister(this);
	}

	@Override
	public void tick()
	{

	}

	@Override
	public int getInterval()
	{
		return 0;
	}

	@Override
	public boolean isUrgent()
	{
		return true;
	}
}
