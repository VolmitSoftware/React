package com.volmit.react.api;

import com.volmit.react.React;
import com.volmit.react.util.SuperSampler;

public abstract class MSampler extends Sampler
{
	protected SuperSampler ss()
	{
		return React.instance.sampleController.getSuperSampler();
	}
}
