package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.React;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SampleFluidTickTime extends MSampler
{
	private IFormatter formatter;

	public SampleFluidTickTime()
	{
		formatter = new IFormatter()
		{
			@Override
			public String from(double d)
			{
				return F.time(d / 1000000.0, 1);
			}
		};
	}

	@Override
	public void construct()
	{
		setName("Fluid Tick"); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.fluid-tick-time.description")); //$NON-NLS-1$
		setID(SampledType.FLUID_TIME.toString());
		setValue(0);
		setColor(C.LIGHT_PURPLE, C.LIGHT_PURPLE);
		setInterval(1);
	}

	@Override
	public void sample()
	{
		setValue(React.instance.fluidController.getaFSMS().getAverage());
	}

	@Override
	public String get()
	{
		return getFormatter().from(getValue()) + ""; //$NON-NLS-1$
	}

	@Override
	public IFormatter getFormatter()
	{
		return formatter;
	}
}
