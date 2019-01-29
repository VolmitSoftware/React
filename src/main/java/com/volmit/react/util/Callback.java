package com.volmit.react.util;

/**
 * Callback for async workers
 *
 * @author cyberpwn
 *
 * @param <T>
 *            the type of object to be returned in the runnable
 */
@SuppressWarnings("hiding")
public class Callback<T> implements Runnable
{
	private T t;

	/**
	 * Execute the callback via async.
	 *
	 * @param t
	 *            the object to be called back from the worker thead
	 */
	public void run(T t)
	{
		this.t = t;
		run();
	}

	/**
	 * Implement your callback code through here. Invoke get(); to get the
	 * called back object
	 */
	@Override
	public void run()
	{

	}

	/**
	 * Get the object from the worker thread
	 *
	 * @return the Object of the defined type T
	 */
	public T get()
	{
		return t;
	}
}
