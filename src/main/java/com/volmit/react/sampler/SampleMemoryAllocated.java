package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SampleMemoryAllocated extends MSampler
{
	private IFormatter formatter;

	public SampleMemoryAllocated()
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
		setName("Alloc Mem"); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.memory-allocated.description")); //$NON-NLS-1$
		setID(SampledType.ALLOCMEM.toString());
		setValue(1);
		setColor(C.GOLD, C.GOLD);
		setInterval(1);
	}

	@Override
	public void sample()
	{
		setValue(ss().getMemoryAllocated());
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
