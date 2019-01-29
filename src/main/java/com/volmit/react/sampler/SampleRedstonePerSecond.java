package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.React;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SampleRedstonePerSecond extends MSampler
{
	private IFormatter formatter;

	public SampleRedstonePerSecond()
	{
		formatter = new IFormatter()
		{
			@Override
			public String from(double d)
			{
				return F.f(d, 0) + Lang.getString("sampler.redstone-second.ps"); //$NON-NLS-1$
			}
		};
	}

	@Override
	public void construct()
	{
		setName("RS/s"); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.redstone-second.description")); //$NON-NLS-1$
		setID(SampledType.REDSTONE_SECOND.toString());
		setValue(0);
		setColor(C.RED, C.RED);
		setInterval(5);
	}

	@Override
	public void sample()
	{
		setValue(React.instance.redstoneController.getaRSS().getAverage());
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
