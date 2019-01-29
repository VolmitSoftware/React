package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.C;
import com.volmit.react.util.F;
import com.volmit.react.util.M;

public class SampleTicksPerSecond extends MSampler
{
	private IFormatter formatter;

	public SampleTicksPerSecond()
	{
		formatter = new IFormatter()
		{
			@Override
			public String from(double d)
			{
				return F.f(getValue() > 19.89 ? 20 : d, 0);
			}
		};
	}

	@Override
	public void construct()
	{
		setName("TPS"); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.tps.description")); //$NON-NLS-1$
		setID(SampledType.TPS.toString());
		setValue(20);
		setColor(C.GREEN, C.GREEN);
		setInterval(1);
	}

	@Override
	public void sample()
	{
		setValue(ss().getTicksPerSecond());
	}

	@Override
	public String get()
	{
		if(-getValue() > 20)
		{
			return (M.r(0.5) ? C.GOLD : C.RED) + Lang.getString("sampler.tps.symbol-frozen") + C.RED + " " + F.time(-getValue(), 1); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return getFormatter().from(getValue()) + Lang.getString("sampler.tps.symbol-stable"); //$NON-NLS-1$
	}

	@Override
	public IFormatter getFormatter()
	{
		return formatter;
	}
}
