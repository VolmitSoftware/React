package com.volmit.react.controller;

import java.lang.reflect.Field;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.volmit.react.Gate;
import com.volmit.react.React;
import com.volmit.react.ReactPlugin;
import com.volmit.react.Surge;
import com.volmit.react.api.ISampler;
import com.volmit.react.api.MemoryTracker;
import com.volmit.react.api.Note;
import com.volmit.react.api.SampledType;
import com.volmit.react.sampler.SampleCPU;
import com.volmit.react.sampler.SampleChunkTime;
import com.volmit.react.sampler.SampleChunksLoaded;
import com.volmit.react.sampler.SampleChunksLoadedPerSecond;
import com.volmit.react.sampler.SampleEntitiesDrops;
import com.volmit.react.sampler.SampleEntitiesLiving;
import com.volmit.react.sampler.SampleEntitiesTiles;
import com.volmit.react.sampler.SampleEntitiesTotal;
import com.volmit.react.sampler.SampleEntityDroppedTicks;
import com.volmit.react.sampler.SampleEntityTime;
import com.volmit.react.sampler.SampleEntityTimeLock;
import com.volmit.react.sampler.SampleExplosionTime;
import com.volmit.react.sampler.SampleFluidPerSecond;
import com.volmit.react.sampler.SampleFluidPerTick;
import com.volmit.react.sampler.SampleFluidTickTime;
import com.volmit.react.sampler.SampleFluidTickUtilization;
import com.volmit.react.sampler.SampleGrowthTime;
import com.volmit.react.sampler.SampleHopperPerSecond;
import com.volmit.react.sampler.SampleHopperPerTick;
import com.volmit.react.sampler.SampleHopperTickTime;
import com.volmit.react.sampler.SampleHopperTickUtilization;
import com.volmit.react.sampler.SampleMemoryAllocated;
import com.volmit.react.sampler.SampleMemoryAllocationPerSecond;
import com.volmit.react.sampler.SampleMemoryFree;
import com.volmit.react.sampler.SampleMemoryMax;
import com.volmit.react.sampler.SampleMemoryTotals;
import com.volmit.react.sampler.SampleMemoryUse;
import com.volmit.react.sampler.SamplePhysicsTickTime;
import com.volmit.react.sampler.SamplePlayerCount;
import com.volmit.react.sampler.SampleReactTaskTime;
import com.volmit.react.sampler.SampleReactTime;
import com.volmit.react.sampler.SampleRedstonePerSecond;
import com.volmit.react.sampler.SampleRedstonePerTick;
import com.volmit.react.sampler.SampleRedstoneTickTime;
import com.volmit.react.sampler.SampleRedstoneTickUtilization;
import com.volmit.react.sampler.SampleTickTime;
import com.volmit.react.sampler.SampleTickUtilization;
import com.volmit.react.sampler.SampleTicksPerSecond;
import com.volmit.react.sampler.SampleTileDroppedTicks;
import com.volmit.react.sampler.SampleTileTime;
import com.volmit.react.sampler.SampleTileTimeLock;
import com.volmit.react.util.AsyncTick;
import com.volmit.react.util.Average;
import com.volmit.react.util.C;
import com.volmit.react.util.Control;
import com.volmit.react.util.Controller;
import com.volmit.react.util.D;
import com.volmit.react.util.Ex;
import com.volmit.react.util.F;
import com.volmit.react.util.I;
import com.volmit.react.util.IController;
import com.volmit.react.util.JSONObject;
import com.volmit.react.util.M;
import com.volmit.react.util.PluginUtil;
import com.volmit.react.util.SuperSampler;
import com.volmit.react.util.TICK;
import com.volmit.react.util.TaskLater;
import com.volmit.react.util.TimingsReport;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;

@AsyncTick
public class SampleController extends Controller
{
	private GMap<String, ISampler> samplers;
	private int cd;
	private int sct;
	public static GMap<String, Double> reports;
	private GList<IController> controllers;
	public static double msu = 0;
	public static double totalTime = 0;
	public static double totalTaskTime = 0;
	public static TimingsReport t = null;
	private SuperSampler ss;
	public long lastTick = M.ms();
	public static MemoryTracker m;
	public static long lt = M.ns();
	public static long lms = 0;
	public static double tps = 0;
	public static Average ta = new Average(10);
	public static long lastGC = M.ms();
	private boolean crashed = false;

	public static void t()
	{
		lms = M.ns() - lt;
		lt = M.ns();
		tps = M.clip((double) lms / (50000000.0), 0.0, 1.0) * 20.0;
		ta.put(tps);
		tps = ta.getAverage();
	}

	@Override
	public void dump(JSONObject object)
	{
		object.put("reports", reports.size());

		JSONObject js = new JSONObject();

		for(String i : samplers.k())
		{
			js.put(i, samplers.get(i).get());
		}

		object.put("samplers", js);
	}

	public SampleController()
	{
		crashed = false;
		controllers = new GList<IController>();
		reports = new GMap<String, Double>();
		sct = 0;
		samplers = new GMap<String, ISampler>();
		constructSamplers();
		cd = 4;
		ss = new SuperSampler();
		ss.start();
		m = new MemoryTracker();
		lastTick = M.ms();
	}

	public void construct()
	{
		controllers = new GList<IController>();

		for(Field i : React.class.getDeclaredFields())
		{
			if(i.isAnnotationPresent(Control.class))
			{
				try
				{
					controllers.add((IController) i.get(React.instance));
				}

				catch(Throwable e)
				{
					Ex.t(e);
				}
			}
		}
	}

	public void report(String key, double d)
	{
		if(Gate.lowMemoryMode)
		{
			return;
		}

		if(!reports.containsKey(key))
		{
			reports.put(key, 0.0);
		}

		reports.put(key, reports.get(key) + d);
	}

	public void registerSampler(ISampler s)
	{
		samplers.put(s.getID(), s);
	}

	public ISampler getSampler(String id)
	{
		return samplers.get(id);
	}

	private void constructSamplers()
	{
		registerSampler(new SampleChunksLoaded());
		registerSampler(new SampleChunksLoadedPerSecond());
		registerSampler(new SampleChunkTime());
		registerSampler(new SampleCPU());
		registerSampler(new SampleEntitiesDrops());
		registerSampler(new SampleEntitiesLiving());
		registerSampler(new SampleEntitiesTiles());
		registerSampler(new SampleEntitiesTotal());
		registerSampler(new SampleEntityDroppedTicks());
		registerSampler(new SampleEntityTime());
		registerSampler(new SampleEntityTimeLock());
		registerSampler(new SampleExplosionTime());
		registerSampler(new SampleFluidPerSecond());
		registerSampler(new SampleFluidPerTick());
		registerSampler(new SampleFluidTickTime());
		registerSampler(new SampleFluidTickUtilization());
		registerSampler(new SampleGrowthTime());
		registerSampler(new SampleHopperPerSecond());
		registerSampler(new SampleHopperPerTick());
		registerSampler(new SampleHopperTickTime());
		registerSampler(new SampleHopperTickUtilization());
		registerSampler(new SampleMemoryAllocated());
		registerSampler(new SampleMemoryAllocationPerSecond());
		registerSampler(new SampleMemoryFree());
		registerSampler(new SampleMemoryMax());
		registerSampler(new SampleMemoryUse());
		registerSampler(new SamplePhysicsTickTime());
		registerSampler(new SampleReactTaskTime());
		registerSampler(new SampleReactTime());
		registerSampler(new SampleRedstonePerSecond());
		registerSampler(new SampleRedstonePerTick());
		registerSampler(new SampleRedstoneTickTime());
		registerSampler(new SampleRedstoneTickUtilization());
		registerSampler(new SampleTicksPerSecond());
		registerSampler(new SampleTickTime());
		registerSampler(new SampleTickUtilization());
		registerSampler(new SampleTileDroppedTicks());
		registerSampler(new SampleTileTime());
		registerSampler(new SampleTileTimeLock());
		registerSampler(new SampleMemoryTotals());
		registerSampler(new SamplePlayerCount());

		for(ISampler i : samplers.v())
		{
			D.v("Sampler: " + i.getID() + " (" + i.getName() + ") @ " + i.getInterval()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			i.construct();
		}
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
		ss.stop();
	}

	@Override
	public void tick()
	{
		if(React.instance.sampleController == null && !crashed)
		{
			crashed = true;
			System.out.println("React Supersampler has crashed. Reloading React.");
			ReactPlugin.reload();
			return;
		}

		t();
		m.tick();
		lastTick = M.ms();

		if(m.isGcd())
		{
			lastGC = M.ms();
			double pct = (double) m.getLastCol() / (double) m.getLastAll();
			Note.GC.bake("GC " + C.WHITE + F.memSize(m.getLastCol(), 0) + C.GRAY + " -> " + C.GOLD + (F.pc(pct)) + " Effective");
			m.setGcd(false);
		}

		ss.onTick();
		I.hit++;

		if(SampledType.TICK.get().getValue() == 0)
		{
			sct++;
		}

		if(TICK.tick % 5 == 0)
		{
			sct -= 2;

			if(sct > 20)
			{
				PluginUtil.reload(ReactPlugin.i);
			}
		}

		reports.clear();
		if(controllers.isEmpty())
		{
			construct();
		}

		for(IController i : controllers)
		{
			report("Controller " + i.getClass().getSimpleName().replaceAll("Controller", ""), i.getTime());
		}

		report("Tasks", msu);
		msu = 0;
		double nsv = 0;
		for(String i : reports.k())
		{
			nsv += reports.get(i);
		}

		totalTime = nsv;
		totalTaskTime = msu;

		if(!crashed && ss.getTpsMonitor().getLastTick() < lastTick && (3 + lastTick) - ss.getTpsMonitor().getLastTick() > 15000)
		{
			crashed = true;
			System.out.println("React Supersampler has crashed. Reloading React.");
			System.out.println("SS has not responded for over " + F.time(lastTick - ss.getTpsMonitor().getLastTick(), 2));
			ReactPlugin.reload();
		}
	}

	public void onTickAsync()
	{
		for(ISampler i : samplers.v())
		{
			try
			{
				if(TICK.tick % i.getInterval() == 0)
				{
					i.sample();
				}
			}

			catch(Throwable e)
			{
				Ex.t(e);
			}
		}
	}

	public SuperSampler getSuperSampler()
	{
		return ss;
	}

	public GMap<String, ISampler> getSamplers()
	{
		return samplers;
	}

	public SuperSampler getSs()
	{
		return ss;
	}

	public int getCd()
	{
		return cd;
	}

	public GList<String> getSamplerNames()
	{
		GList<String> samps = new GList<String>();

		for(String i : getSamplers().k())
		{
			samps.add(i);
		}

		return samps;
	}

	@EventHandler
	public void on(PlayerCommandPreprocessEvent e)
	{
		if(e.getMessage().equalsIgnoreCase("/timings on"))
		{
			if(t != null)
			{
				t.stop();
				t = null;
			}

			t = new TimingsReport();
			t.start();
			Gate.msgActing(e.getPlayer(), "Timings Started");
		}

		if(e.getMessage().equalsIgnoreCase("/timings off"))
		{
			if(t != null)
			{
				t.stop();
				t = null;
				Gate.msgSuccess(e.getPlayer(), "Timings Stopped");
			}
		}

		if(e.getMessage().equalsIgnoreCase("/timings reset"))
		{
			if(t != null)
			{
				t.stop();
				t = null;
			}

			t = new TimingsReport();
			t.start();
			Gate.msgSuccess(e.getPlayer(), "Timings Reset");
		}

		if(e.getMessage().equalsIgnoreCase("/timings paste"))
		{
			if(t != null)
			{
				t.stop();
				t.reportTo(e.getPlayer());
				t = null;
			}
		}

		if(e.getMessage().equalsIgnoreCase("/timings peek"))
		{
			if(t != null)
			{
				t.reportTo(e.getPlayer());
				Gate.msgActing(e.getPlayer(), "Timings Peeked. Timings will continue to sample.");
			}

			e.setCancelled(true);
		}

		if(e.getMessage().equalsIgnoreCase("/timings spam"))
		{
			if(t != null)
			{
				Gate.msgActing(e.getPlayer(), "Timings data will be sent 4 times a second.");
				Gate.msgActing(e.getPlayer(), "You can turn this off by using /timings spam");

				new TaskLater("tf", 45)
				{
					@Override
					public void run()
					{
						if(t != null)
						{
							t.spam(e.getPlayer());
						}
					}
				};
			}

			e.setCancelled(true);
		}
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
