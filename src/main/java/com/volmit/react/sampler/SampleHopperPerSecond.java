package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.React;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SampleHopperPerSecond extends MSampler
{
	private IFormatter formatter;

	public SampleHopperPerSecond()
	{
		formatter = new IFormatter()
		{
			@Override
			public String from(double d)
			{
				return F.f(d, 0) + Lang.getString("sampler.hopper-per-second.ps"); //$NON-NLS-1$
			}
		};
	}

	@Override
	public void construct()
	{
		setName(Lang.getString("sampler.hopper-per-second.name")); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.hopper-per-second.description")); //$NON-NLS-1$
		setID(SampledType.HOPPER_SECOND.toString());
		setValue(0);
		setColor(C.RED, C.RED);
		setInterval(5);
	}

	@Override
	public void sample()
	{
		setValue(React.instance.hopperController.getaHSS().getAverage());
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
