package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SampleMemoryUse extends MSampler
{
	private IFormatter formatter;

	public SampleMemoryUse()
	{
		formatter = new IFormatter()
		{
			@Override
			public String from(double d)
			{
				return F.memSize((long) d);
			}
		};
	}

	@Override
	public void construct()
	{
		setName(Lang.getString("sampler.memory-used.name")); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.memory-used.description")); //$NON-NLS-1$
		setID(SampledType.MEM.toString());
		setValue(1);
		setColor(C.GOLD, C.GOLD);
		setInterval(20);
	}

	@Override
	public void sample()
	{
		setValue(ss().getMemoryUse());
	}

	@Override
	public String get()
	{
		return getFormatter().from(getValue());
	}

	@Override
	public IFormatter getFormatter()
	{
		return formatter;
	}
}
