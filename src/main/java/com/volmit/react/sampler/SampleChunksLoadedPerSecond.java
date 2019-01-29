package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.Average;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SampleChunksLoadedPerSecond extends MSampler
{
	private Average a = new Average(2);
	private IFormatter formatter;

	public SampleChunksLoadedPerSecond()
	{
		formatter = new IFormatter()
		{
			@Override
			public String from(double d)
			{
				return F.f((int) d);
			}
		};
	}

	@Override
	public void construct()
	{
		setName(Lang.getString("sampler.chunks-per-second.chunks-sec")); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.chunks-per-second.chunks-per-second")); //$NON-NLS-1$
		setID(SampledType.CHKS.toString());
		setValue(1);
		setColor(C.RED, C.RED);
		setInterval(15);
	}

	@Override
	public void sample()
	{
		a.put(ss().getChunksLoaded());
		setValue(a.getAverage());
	}

	@Override
	public String get()
	{
		return Lang.getString("sampler.chunks-per-second.symbol") + getFormatter().from(getValue()) + Lang.getString("sampler.chunks-per-second.ps"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public IFormatter getFormatter()
	{
		return formatter;
	}
}
