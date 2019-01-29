package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.React;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.C;
import com.volmit.react.util.Ex;
import com.volmit.react.util.F;

public class SampleHopperPerTick extends MSampler
{
	private IFormatter formatter;

	public SampleHopperPerTick()
	{
		formatter = new IFormatter()
		{
			@Override
			public String from(double d)
			{
				return F.f(d, 0) + Lang.getString("sampler.hopper-per-tick.pt"); //$NON-NLS-1$
			}
		};

	}

	@Override
	public void construct()
	{
		setName(Lang.getString("sampler.hopper-per-tick.name")); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.hopper-per-tick.description")); //$NON-NLS-1$
		setID(SampledType.HOPPER_TICK.toString());
		setValue(0);
		setColor(C.RED, C.RED);
		setInterval(1);
	}

	@Override
	public void sample()
	{
		try
		{
			setValue(React.instance.hopperController.getaHST().getAverage());
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
		return getFormatter().from(getValue());
	}

	@Override
	public IFormatter getFormatter()
	{
		return formatter;
	}
}
