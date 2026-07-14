package io.github.pepe3012.chronocycles.api;

/**
 * Defines how the natural Minecraft weather cycle advances.
 *
 * <p>This mode does not prevent scheduled weather events from being applied.</p>
 */
public enum WeatherCycleMode {
    /**
     * Prevents the natural weather cycle from advancing.
     */
    NEVER(0),

    /**
     * Advances the natural weather cycle at the rate configured by the
     * {@link ChronocyclesGameRules#TIME_SCALE} gamerule.
     */
    SCALED(1),

    /**
     * Advances the natural weather cycle once per server tick, independently
     * of the configured time scale.
     */
    VANILLA(2);

    private final int id;

    WeatherCycleMode(int id) {
        this.id = id;
    }

    /**
     * Returns the serialized gamerule value associated with this mode.
     *
     * @return this mode's numeric identifier
     */
    public int getId() {
        return this.id;
    }

    /**
     * Returns the weather cycle mode associated with the specified identifier.
     *
     * @param id the serialized gamerule value
     * @return the corresponding weather cycle mode
     * @throws IllegalArgumentException if the identifier is unknown
     */
    public static WeatherCycleMode byId(int id) {
        return switch (id) {
            case 0 -> NEVER;
            case 1 -> SCALED;
            case 2 -> VANILLA;
            default -> throw new IllegalArgumentException("Unknown weather cycle mode: " + id);
        };
    }
}