package com.volmit.react.util;

public enum ServerState
{
	/**
	 * A server START. Not a reload. Use this to execute only server startup things.
	 * This will not be the current state if you check this on enable, and the
	 * plugin was enabled while running
	 */
	START,

	/**
	 * A server plugin ENABLE. Not a start. This happens when a plugin is enaled, or
	 * loaded AFTER startup. While the server is running, for example plugman loads
	 * and such.
	 */
	ENABLE,

	/**
	 * A server plugin DISABLE. Not a stop, this happens when a plugin is disabled
	 * or unloaded AFTER the server is already running, and is NOT stopping. For
	 * example plugman unloads and such.
	 */
	DISABLE,

	/**
	 * A server STOP. Not a reload. Use this to execute only server shutdown things.
	 * This will not be the current state if you check this on disable, and the
	 * plugin was disabled while running
	 */
	STOP,

	/**
	 * The server is running. The state of plugins should be enabled, unless
	 * changed, and the server is not stopping.
	 */
	RUNNING;
}
