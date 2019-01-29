package com.volmit.react.util;

import com.volmit.volume.lang.collections.GList;

public interface SurgePlugin
{
	/**
	 * Called after surge has set itself up (effective onEnable)
	 *
	 * @param serverProtocol
	 *            the minecraft version
	 */
	public void onStart(Protocol serverProtocol);

	/**
	 * Called after surge has shut down all tasks and threads, clean up here
	 * (effective onDisable)
	 */
	public void onStop();

	/**
	 * Called after onLoad(), only some of the Surge service apis are loaded up here
	 */
	public void onPostInit();

	/**
	 * Not the best way to start off a day. This is called literally just after the
	 * plugin jar file is loaded up and this plugin object is initialized. Surge
	 * apis which require services will not be online here yet. Be safe.
	 */
	public void onPreInit();

	/**
	 * Called when it is time to register controllers
	 */
	public void onControllerRegistry();

	/**
	 * Register a controller
	 *
	 * @param c
	 *            the controller
	 */
	public void registerController(IController c);

	/**
	 * Get all active controllers
	 *
	 * @return a list of controllable objects
	 */
	public GList<IController> getControllers();

	/**
	 * Get the threadPool
	 *
	 * @return the pool manager
	 */
	public ParallelPoolManager getThreadPool();
}
