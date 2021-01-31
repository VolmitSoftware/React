package com.volmit.react.util;

import primal.lang.collection.GList;

/**
 * An embedded interface which can hold meta instances of itself, or only
 * itself, executions of the interface should execute the sub-interfaced
 * instances alike. This allows you to create customizable object trees for
 * speedy functionality
 *
 * @param <T> the interface to be embedded.
 * @author cyberpwn
 */
@SuppressWarnings("hiding")
public interface Embedded<T> {
    /**
     * Get a list of embedded objects
     *
     * @return the objects
     */
    GList<T> get();

    /**
     * Add an embedded object to this embedded object
     *
     * @param t the embedded object
     */
    void add(T t);
}
