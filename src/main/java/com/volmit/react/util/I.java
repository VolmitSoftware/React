package com.volmit.react.util;

import com.volmit.volume.lang.collections.GMap;

public class I
{
	public static GMap<String, Average> m = new GMap<String, Average>();
	public static GMap<String, Profiler> v = new GMap<String, Profiler>();
	public static GMap<String, Long> h = new GMap<String, Long>();
	public static GMap<String, Double> y = new GMap<String, Double>();
	public static long hit = 0;

	public static void g()
	{
		hit++;
	}

	public static void a(String name, int avg)
	{
		if(!m.containsKey(name))
		{
			m.put(name, new Average(avg));
		}

		if(!h.containsKey(name))
		{
			h.put(name, 0l);
		}

		if(!y.containsKey(name))
		{
			y.put(name, 0.0);
		}

		Profiler pr = new Profiler();
		pr.begin();
		v.put(name, pr);
	}

	public static double b(String name)
	{
		if(v.containsKey(name) && m.containsKey(name) && y.containsKey(name) && h.containsKey(name))
		{
			h.put(name, h.get(name) + 1);
			Profiler pr = v.get(name);
			pr.end();
			y.put(name, y.get(name) + pr.getMilliseconds());
			m.get(name).put(pr.getMilliseconds());

			return pr.getMilliseconds();
		}

		return 0;
	}
}