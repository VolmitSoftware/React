package com.volmit.react.sampler;

import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.controller.SampleController;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SampleReactTime extends MSampler
{
	private IFormatter formatter;

	public SampleReactTime()
	{
		formatter = new IFormatter()
		{
			@Override
			public String from(double d)
			{
				return F.time(d, 2);
			}
		};
	}

	@Override
	public void construct()
	{
		setName("R Time"); //$NON-NLS-1$
		setDescription("Samples react total tick time"); //$NON-NLS-1$
		setID(SampledType.REACT_TIME.toString());
		setValue(0);
		setColor(C.AQUA, C.AQUA);
		setInterval(1);
	}

	@Override
	public void sample()
	{
		setValue(SampleController.totalTime);
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
