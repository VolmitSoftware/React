package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.Average;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SampleTickUtilization extends MSampler
{
	private IFormatter formatter;
	private Average aa = new Average(50);

	public SampleTickUtilization()
	{
		formatter = new IFormatter()
		{
			@Override
			public String from(double d)
			{
				return F.pc(d, 0);
			}
		};
	}

	@Override
	public void construct()
	{
		setName("TIU"); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.tick-utilization.description")); //$NON-NLS-1$
		setID(SampledType.TIU.toString());
		setValue(1);
		setColor(C.GREEN, C.GREEN);
		setInterval(1);
	}

	@Override
	public void sample()
	{
		aa.put(ss().getTickUtilization());
		setValue(aa.getAverage());
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
