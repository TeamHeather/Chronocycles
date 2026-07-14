package org.heather.chronocycles.api;

/**
 * Defines the weather state applied by a scheduled weather event.
 */
public enum WeatherType {
    /**
     * Clear weather without rain or thunder.
     */
    CLEAR,

    /**
     * Rain without thunder.
     */
    RAIN,

    /**
     * Rain accompanied by thunder.
     */
    THUNDER
}