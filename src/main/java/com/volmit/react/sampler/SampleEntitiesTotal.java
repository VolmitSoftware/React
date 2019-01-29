package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SampleEntitiesTotal extends MSampler
{
	private IFormatter formatter;

	public SampleEntitiesTotal()
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
		setName(Lang.getString("sampler.entities-total.name")); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.entities-total.description")); //$NON-NLS-1$
		setID(SampledType.ENT.toString());
		setValue(0);
		setColor(C.AQUA, C.AQUA);
		setInterval(40);
	}

	@Override
	public void sample()
	{
		setValue(ss().getTotalEntities());
	}

	@Override
	public String get()
	{
		return getFormatter().from(getValue()) + Lang.getString("sampler.entities-total.symbol"); //$NON-NLS-1$
	}

	@Override
	public IFormatter getFormatter()
	{
		return formatter;
	}
}
