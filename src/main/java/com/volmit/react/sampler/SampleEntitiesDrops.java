package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SampleEntitiesDrops extends MSampler
{
	private IFormatter formatter;

	public SampleEntitiesDrops()
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
		setName(Lang.getString("sampler.entities-drops.name")); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.entities-drops.description")); //$NON-NLS-1$
		setID(SampledType.ENTDROP.toString());
		setValue(0);
		setColor(C.AQUA, C.AQUA);
		setInterval(40);
	}

	@Override
	public void sample()
	{
		setValue(ss().getTotalDrops());
	}

	@Override
	public String get()
	{
		return Lang.getString("sampler.entities-drops.symbol") + getFormatter().from(getValue()); //$NON-NLS-1$
	}

	@Override
	public IFormatter getFormatter()
	{
		return formatter;
	}
}
