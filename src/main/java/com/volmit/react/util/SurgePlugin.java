package com.volmit.react.util;

import primal.lang.collection.GList;

public interface SurgePlugin {
    /**
     * Called after surge has set itself up (effective onEnable)
     *
     * @param serverProtocol the minecraft version
     */
    void onStart(Protocol serverProtocol);

    /**
     * Called after surge has shut down all tasks and threads, clean up here
     * (effective onDisable)
     */
    void onStop();

    /**
     * Called after onLoad(), only some of the Surge service apis are loaded up here
     */
    void onPostInit();

    /**
     * Not the best way to start off a day. This is called literally just after the
     * plugin jar file is loaded up and this plugin object is initialized. Surge
     * apis which require services will not be online here yet. Be safe.
     */
    void onPreInit();

    /**
     * Called when it is time to register controllers
     */
    void onControllerRegistry();

    /**
     * Register a controller
     *
     * @param c the controller
     */
    void registerController(IController c);

    /**
     * Get all active controllers
     *
     * @return a list of controllable objects
     */
    GList<IController> getControllers();

    /**
     * Get the threadPool
     *
     * @return the pool manager
     */
    ParallelPoolManager getThreadPool();
}
