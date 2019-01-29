package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SampleMemoryAllocationPerSecond extends MSampler
{
	private IFormatter formatter;

	public SampleMemoryAllocationPerSecond()
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
		setName("MAH/s"); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.mahs.description")); //$NON-NLS-1$
		setID(SampledType.MAHS.toString());
		setValue(1);
		setColor(C.GOLD, C.GOLD);
		setInterval(10);
	}

	@Override
	public void sample()
	{
		setValue(ss().getMahs());
	}

	@Override
	public String get()
	{
		return "\u21AF " + getFormatter().from(getValue()); //$NON-NLS-1$
	}

	@Override
	public IFormatter getFormatter()
	{
		return formatter;
	}
}
