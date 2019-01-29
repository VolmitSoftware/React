package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.React;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SampleTileTimeLock extends MSampler
{
	private IFormatter formatter;

	public SampleTileTimeLock()
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
		setName("Tile TLCK"); //$NON-NLS-1$
		setDescription(Lang.getString("sampler.tile-time-lock.description")); //$NON-NLS-1$
		setID(SampledType.TILE_TIME_LOCK.toString());
		setValue(0);
		setColor(C.LIGHT_PURPLE, C.LIGHT_PURPLE);
		setInterval(1);
	}

	@Override
	public void sample()
	{
		setValue(React.instance.smearTickController.getUniversalTileLimit());
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
