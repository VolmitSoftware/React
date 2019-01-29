package com.volmit.react.controller;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.Surge;
import com.volmit.react.api.IRAI;
import com.volmit.react.api.RAI;
import com.volmit.react.util.A;
import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;
import com.volmit.react.util.TICK;

public class RAIController extends Controller
{
	private IRAI rai;
	public boolean raiEnabled;

	@Override
	public void dump(JSONObject object)
	{
		object.put("active", raiEnabled);
	}

	@Override
	public void start()
	{
		Surge.register(this);
		rai = new RAI();
		raiEnabled = true;
	}

	@Override
	public void stop()
	{
		Surge.unregister(this);
	}

	@Override
	public void tick()
	{
		if(!Config.RAI)
		{
			return;
		}

		if(Config.MONITOR_ONLY)
		{
			return;
		}

		if(Gate.isLowMemory() && TICK.tick % 5 != 0)
		{
			return;
		}

		new A()
		{
			@Override
			public void run()
			{
				rai.tick();
			}
		};
	}

	public IRAI getRai()
	{
		return rai;
	}

	@Override
	public int getInterval()
	{
		return 1;
	}

	@Override
	public boolean isUrgent()
	{
		return true;
	}
}
