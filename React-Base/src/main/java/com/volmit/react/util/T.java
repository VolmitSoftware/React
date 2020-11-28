package com.volmit.react.util;

/**
 * Quick timer
 * 
 * @author cyberpwn
 */
public abstract class T extends Timer
{
	/**
	 * Automatically creates and starts the timer
	 */
	public T()
	{
		super();
		start();
	}
	
	/**
	 * Called when the timer has been stopped
	 * 
	 * @param nsTime
	 *            the long time in nanoseconds
	 * @param msTime
	 *            the double time in milliseconds
	 */
	public abstract void onStop(long nsTime, double msTime);
	
	@Override
	public void stop()
	{
		super.stop();
		
		onStop(getTime(), ((double) getTime() / 1000000.0));
	}
}
