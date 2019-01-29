package com.volmit.react.sampler;

import org.bukkit.Bukkit;

import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.C;
import com.volmit.react.util.F;

public class SamplePlayerCount extends MSampler
{
	private IFormatter formatter;

	public SamplePlayerCount()
	{
		formatter = new IFormatter()
		{
			@Override
			public String from(double d)
			{
				return F.f((int) d) + " PLR"; //$NON-NLS-1$
			}
		};
	}

	@Override
	public void construct()
	{
		setName("PLR"); //$NON-NLS-1$
		setDescription("Player Count"); //$NON-NLS-1$
		setID(SampledType.PLAYERCOUNT.toString());
		setValue(0);
		setColor(C.AQUA, C.AQUA);
		setInterval(1);
	}

	@Override
	public void sample()
	{
		setValue(Bukkit.getOnlinePlayers().size());
	}

	@Override
	public String get()
	{
		return getFormatter().from(getValue()); // $NON-NLS-1$
	}

	@Override
	public IFormatter getFormatter()
	{
		return formatter;
	}
}
