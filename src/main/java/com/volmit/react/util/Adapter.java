package com.volmit.react.util;

/**
 * An adapter converts one object into another
 *
 * @param <FROM> the given object
 * @param <TO>   the resulting object
 * @author cyberpwn
 */
public interface Adapter<FROM, TO> {
    /**
     * Adapt an object
     *
     * @param from the from object
     * @return the adapted object
     */
    TO adapt(FROM from);

    /**
     * Handles the adapter processing
     *
     * @param from the from object
     * @return the adapted object
     */
    TO onAdapt(FROM from);
}
