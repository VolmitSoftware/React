package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.React;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SampleEntityTime extends MSampler
{
	private IFormatter formatter;

	public SampleEntityTime()
	{
		formatter = new IFormatter()
		{
			@Override
			public String from(double d)
			{
				return F.time(d, 1);
			}
		};
	}

	@Override
	public void construct()
	{
		setName("Ent TIME"); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.entity-time.description")); //$NON-NLS-1$
		setID(SampledType.ENTITY_TIME.toString());
		setValue(0);
		setColor(C.LIGHT_PURPLE, C.LIGHT_PURPLE);
		setInterval(1);
	}

	@Override
	public void sample()
	{
		setValue(React.instance.smearTickController.getUniversalEntityTick());
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
