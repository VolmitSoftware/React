package com.volmit.react.legacy.server;

import com.volmit.react.React;
import com.volmit.react.api.SampledType;
import com.volmit.volume.lang.collections.GMap;

public class ReactData
{
	private GMap<String, Double> samples;

	public ReactData()
	{
		samples = new GMap<String, Double>();
	}

	public void sample(React react)
	{
		for(SampledType s : SampledType.values())
		{
			samples.put(s.get().getID().toLowerCase(), s.get().getValue());
		}
	}

	public GMap<String, Double> getSamples()
	{
		return samples;
	}
}
