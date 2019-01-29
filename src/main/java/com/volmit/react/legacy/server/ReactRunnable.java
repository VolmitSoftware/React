package com.volmit.react.legacy.server;

import com.volmit.react.React;

public class ReactRunnable implements Runnable
{
	private React react;

	public void run(React react)
	{
		this.react = react;
		run();
	}

	@Override
	public void run()
	{

	}

	public React getReact()
	{
		return react;
	}
}
