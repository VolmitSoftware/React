package com.volmit.react.sampler;

import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.controller.SampleController;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SampleMemoryTotals extends MSampler
{
	private IFormatter formatter;

	public SampleMemoryTotals()
	{
		formatter = new IFormatter()
		{
			@Override
			public String from(double d)
			{
				return "+" + F.memSize(SampleController.m.getAllocated(), 0) + " -> " + F.memSize(SampleController.m.getTotalCollected(), 0);
			}
		};
	}

	@Override
	public void construct()
	{
		setName("memtotals"); //$NON-NLS-1$
		setDescription("Samples memory totals"); //$NON-NLS-1$
		setID(SampledType.MEMTOTALS.toString());
		setValue(1);
		setColor(C.GOLD, C.GOLD);
		setInterval(40);
	}

	@Override
	public void sample()
	{
		setValue(1);
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
