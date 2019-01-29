package com.volmit.react.sampler;

import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.A;
import com.volmit.react.util.Average;
import com.volmit.react.util.C;
import com.volmit.react.util.F;
import com.volmit.react.util.M;
import com.volmit.react.util.Platform;

public class SampleCPU extends MSampler
{
	private IFormatter formatter;
	private Average aa = new Average(3);
	private long lastSample = M.ms();

	public SampleCPU()
	{
		formatter = new IFormatter()
		{
			@Override
			public String from(double d)
			{
				return F.pc(d, 0) + " CPU"; //$NON-NLS-1$
			}
		};
	}

	@Override
	public void construct()
	{
		setName("CPU"); //$NON-NLS-1$
		setDescription("Java Process Usage"); //$NON-NLS-1$
		setID(SampledType.CPU.toString());
		setValue(0);
		setColor(C.GREEN, C.GREEN);
		setInterval(20);
	}

	@Override
	public void sample()
	{
		if(M.ms() - lastSample < 750)
		{
			return;
		}

		lastSample = M.ms();
		new A()
		{
			@Override
			public void run()
			{
				aa.put(Platform.CPU.getLiveProcessCPULoad());
				setValue(aa.getAverage());
			}
		};
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
