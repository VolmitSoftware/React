package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SampleEntitiesTiles extends MSampler
{
	private IFormatter formatter;

	public SampleEntitiesTiles()
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
		setName(Lang.getString("sampler.entities-tiles.name")); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.entities-tiles.description")); //$NON-NLS-1$
		setID(SampledType.ENTTILE.toString());
		setValue(0);
		setColor(C.AQUA, C.AQUA);
		setInterval(40);
	}

	@Override
	public void sample()
	{
		setValue(ss().getTotalTiles());
	}

	@Override
	public String get()
	{
		return Lang.getString("sampler.entities-tiles.symbol") + F.f((int) getValue()); //$NON-NLS-1$
	}

	@Override
	public IFormatter getFormatter()
	{
		return formatter;
	}
}
