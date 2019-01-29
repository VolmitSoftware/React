package com.volmit.react.util;

import com.volmit.volume.lang.collections.GList;

/**
 * An embedded interface which can hold meta instances of itself, or only
 * itself, executions of the interface should execute the sub-interfaced
 * instances alike. This allows you to create customizable object trees for
 * speedy functionality
 *
 * @author cyberpwn
 *
 * @param <T>
 *            the interface to be embedded.
 */
@SuppressWarnings("hiding")
public interface Embedded<T>
{
	/**
	 * Get a list of embedded objects
	 *
	 * @return the objects
	 */
	public GList<T> get();

	/**
	 * Add an embedded object to this embedded object
	 *
	 * @param t
	 *            the embedded object
	 */
	public void add(T t);
}
