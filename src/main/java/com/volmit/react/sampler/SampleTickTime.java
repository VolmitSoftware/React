package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.Average;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SampleTickTime extends MSampler
{
	private IFormatter formatter;
	private Average aa = new Average(10);

	public SampleTickTime()
	{
		formatter = new IFormatter()
		{
			@Override
			public String from(double d)
			{
				return F.f(d, 0) + Lang.getString("sampler.tick-time.milliseconds"); //$NON-NLS-1$
			}
		};
	}

	@Override
	public void construct()
	{
		setName(Lang.getString("sampler.tick-time.name")); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.tick-time.description")); //$NON-NLS-1$
		setID(SampledType.TICK.toString());
		setValue(1);
		setColor(C.GREEN, C.GREEN);
		setInterval(1);
	}

	@Override
	public void sample()
	{
		aa.put(ss().getTickTime());
		setValue(ss().getTickTime());
	}

	@Override
	public String get()
	{
		return getFormatter().from(getValue()); // $NON-NLS-1$
	}

	@Override
	public IFormatter getFormatter()
	{
		return formatter;
	}
}
