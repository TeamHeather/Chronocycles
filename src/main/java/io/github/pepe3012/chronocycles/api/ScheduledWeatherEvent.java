package io.github.pepe3012.chronocycles.api;

import java.util.Objects;

/**
 * Represents a weather event scheduled against the absolute overworld clock.
 *
 * @param startTick the inclusive event start tick
 * @param endTick the exclusive event end tick
 * @param weather the weather applied during the event
 */
public record ScheduledWeatherEvent(long startTick, long endTick, WeatherType weather) {
    /**
     * Creates and validates a scheduled weather event.
     */
    public ScheduledWeatherEvent {
        Objects.requireNonNull(weather, "weather");

        if (startTick < 0L) throw new IllegalArgumentException("Start tick cannot be negative.");
        if (endTick <= startTick) throw new IllegalArgumentException("End tick must be after the start tick.");
    }

    /**
     * Returns the duration of this event.
     *
     * @return the duration in world-clock ticks
     */
    public long duration() {
        return this.endTick - this.startTick;
    }
}