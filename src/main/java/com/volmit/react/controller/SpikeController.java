package com.volmit.react.controller;

import org.bukkit.plugin.Plugin;

import com.volmit.react.Lang;
import com.volmit.react.React;
import com.volmit.react.ReactPlugin;
import com.volmit.react.api.Note;
import com.volmit.react.api.Unused;
import com.volmit.react.util.A;
import com.volmit.react.util.AsyncTick;
import com.volmit.react.util.CPS;
import com.volmit.react.util.Callback;
import com.volmit.react.util.Controller;
import com.volmit.react.util.D;
import com.volmit.react.util.Ex;
import com.volmit.react.util.JSONObject;
import com.volmit.react.util.Task;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;
import com.volmit.volume.lang.collections.GSet;

@AsyncTick
public class SpikeController extends Controller
{
	private GMap<String, Integer> spikes = new GMap<String, Integer>();

	@Override
	public void dump(JSONObject object)
	{
		JSONObject j = new JSONObject();

		for(String i : spikes.k())
		{
			j.put(i, spikes.get(i));
		}

		object.put("spikes", j);
	}

	@Override
	public void start()
	{
		new Task("waiter") //$NON-NLS-1$
		{
			@Override
			public void run()
			{
				new A()
				{
					@Override
					public void run()
					{
						D.l(Lang.getString("controller.spike-manager.scanning-plugins")); //$NON-NLS-1$
						try
						{
							CPS.scan();
							D.l(Lang.getString("controller.spike-manager.completed")); //$NON-NLS-1$
						}

						catch(Throwable e)
						{
							Ex.t(e);
						}
					}
				};
			}
		};
	}

	@Override
	public void stop()
	{

	}

	public void onTick()
	{
		GMap<Long, GList<StackTraceElement>> vv = React.instance.sampleController.getSuperSampler().getSpikes().copy();
		React.instance.sampleController.getSuperSampler().getSpikes().clear();

		new A()
		{
			@Override
			public void run()
			{
				try
				{
					for(long i : vv.k())
					{
						GSet<String> gv = new GSet<String>();

						for(StackTraceElement j : vv.get(i))
						{
							for(Plugin k : CPS.identify(j.getClassName()))
							{
								if(!gv.contains(k.getName()))
								{
									gv.add(k.getName());
								}
							}
						}

						for(String j : gv)
						{
							if(!spikes.contains(j))
							{
								spikes.put(j, 0);
							}

							spikes.put(j, spikes.get(j) + 1);
							Note.SPIKES.bake("Spike -> " + j + " (" + spikes.get(j) + " time" + (spikes.get(j) == 1 ? "" : "s") + ")");
						}
					}
				}

				catch(Throwable e)
				{
					Ex.t(e);
				}
			}
		};
	}

	@Unused
	@Override
	public void tick()
	{
		onTick();
	}

	public GMap<String, Integer> getSpikes()
	{
		return spikes;
	}

	public void whoFuckingDidThis(Callback<Plugin> callback)
	{
		Thread t = Thread.currentThread();

		new A()
		{
			@Override
			public void run()
			{
				StackTraceElement[] els = t.getStackTrace();
				Plugin plg = null;
				GList<Plugin> plgs = new GList<Plugin>();

				for(StackTraceElement i : els)
				{
					for(Plugin j : CPS.identify(i.getClassName()))
					{
						if(j == null || ReactPlugin.i == null)
						{
							continue;
						}

						if(j.equals(ReactPlugin.i))
						{
							continue;
						}

						plgs.add(j);
					}
				}

				if(!plgs.isEmpty())
				{
					plg = plgs.mostCommon();
				}

				callback.run(plg);
			}
		};
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
