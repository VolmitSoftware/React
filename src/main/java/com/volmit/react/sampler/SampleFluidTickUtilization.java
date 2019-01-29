package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.React;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.C;
import com.volmit.react.util.Ex;
import com.volmit.react.util.F;

public class SampleFluidTickUtilization extends MSampler
{
	private IFormatter formatter;

	public SampleFluidTickUtilization()
	{
		formatter = new IFormatter()
		{
			@Override
			public String from(double d)
			{
				return F.pc(d, 1);
			}
		};
	}

	@Override
	public void construct()
	{
		setName("Fluid TIU"); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.fluid-tick-utilization.description")); //$NON-NLS-1$
		setID(SampledType.FLUID_TICK_USAGE.toString());
		setValue(0);
		setColor(C.RED, C.RED);
		setInterval(1);
	}

	@Override
	public void sample()
	{
		try
		{
			setValue(React.instance.fluidController.getaFSMS().getAverage() / 50000000.0);
		}

		catch(Throwable e)
		{
			Ex.t(e);
			setValue(0);
		}
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
