package com.volmit.react.api;

import com.volmit.react.Config;
import com.volmit.react.util.Ex;
import com.volmit.react.util.M;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;

public class PlotBoard
{
	private GMap<Long, Double> plots;

	public PlotBoard()
	{
		plots = new GMap<Long, Double>();
	}

	public void clean()
	{
		while(plots.size() > Config.SAMPLE_POINTS)
		{
			plots.remove(earilestRecording());
		}
	}

	public long getInaccurateTimeSpan()
	{
		return getMinimumAccuracyRange() - earilestRecording();
	}

	public long getAccurateTimeSpan()
	{
		return latestRecording() - getMinimumAccuracyRange();
	}

	public long getMedian(long from, long to)
	{
		double k = 0;
		double c = 0;

		for(Long i : plots.k())
		{
			if(within(from, to, i))
			{
				k += i;
				c++;
			}
		}

		return (long) (k / c);
	}

	public long getMinimumAccuracyRange()
	{
		return getMedian(getMedian(earilestRecording(), latestRecording()), latestRecording());
	}

	public double getMaxValue()
	{
		double min = Double.MIN_VALUE;

		for(long i : plots.k())
		{
			if(plots.get(i) > min)
			{
				min = plots.get(i);
			}
		}

		return min;
	}

	public double getMinValue()
	{
		double min = Double.MAX_VALUE;

		for(long i : plots.k())
		{
			if(plots.get(i) < min)
			{
				min = plots.get(i);
			}
		}

		return min;
	}

	public GList<Double> sort(GMap<Long, Double> msa)
	{
		return msa.sortV();
	}

	public long earilestRecording()
	{
		long min = Long.MAX_VALUE;

		for(long i : plots.k())
		{
			if(i < min)
			{
				min = i;
			}
		}

		return min;
	}

	public long latestRecording()
	{
		long min = Long.MIN_VALUE;

		for(long i : plots.k())
		{
			if(i > min)
			{
				min = i;
			}
		}

		return min;
	}

	public void reduceBefore(long before)
	{
		reduceBetween(earilestRecording(), before);
	}

	public void reduceBetween(long from, long to)
	{
		boolean other = false;
		double old = 0;

		for(long i : getBetween(from, to).k())
		{
			other = !other;

			try
			{
				if(!other && plots.containsKey(i))
				{
					old = plots.get(i);
					plots.remove(i);
				}

				else
				{
					plots.put(i, (old + plots.get(i)) / 2);
				}
			}

			catch(Throwable e)
			{
				Ex.t(e);
			}
		}
	}

	public boolean before(long from, long at)
	{
		return within(0, from, at);
	}

	public boolean after(long from, long at)
	{
		return within(from, M.ms(), at);
	}

	public boolean within(long from, long to, long at)
	{
		return at >= from && at <= to;
	}

	public GMap<Long, Double> getBetween(long from, long to)
	{
		GMap<Long, Double> m = new GMap<Long, Double>();

		for(Long i : plots.k())
		{
			try
			{
				if(within(from, to, i))
				{
					m.put(i, plots.get(i));
				}
			}

			catch(Throwable e)
			{
				Ex.t(e);
			}
		}

		return m;
	}

	public void plot(long l, double v)
	{
		plots.put(l, v);
		clean();
	}

	public boolean hasAt(long l)
	{
		return plots.containsKey(l);
	}

	public double getAt(long l)
	{
		return plots.get(l);
	}

	public int getSize()
	{
		return plots.size();
	}
}
