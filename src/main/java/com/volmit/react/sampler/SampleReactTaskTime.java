package com.volmit.react.sampler;

import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.C;
import com.volmit.react.util.F;
import com.volmit.react.util.S;

public class SampleReactTaskTime extends MSampler
{
	private IFormatter formatter;

	public SampleReactTaskTime()
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
		setName("RTT"); //$NON-NLS-1$
		setDescription("Samples react task queue"); //$NON-NLS-1$
		setID(SampledType.REACT_TASK_TIME.toString());
		setValue(0);
		setColor(C.AQUA, C.AQUA);
		setInterval(1);
	}

	@Override
	public void sample()
	{
		setValue(S.mgr.getSqueue().size());
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
