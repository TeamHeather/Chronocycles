package io.github.pepe3012.chronocycles.api;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.WeatherData;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Controls overworld time progression and scheduled weather events.
 *
 * <p>Chronocycles disables vanilla time progression and advances the overworld
 * clock according to {@link ChronocyclesGameRules#TIME_SCALE}. Natural weather
 * behavior is controlled by {@link ChronocyclesGameRules#WEATHER_CYCLE_MODE}.</p>
 *
 * <p>Scheduled weather events use absolute overworld clock ticks. They currently
 * remain in memory and are not preserved across server restarts.</p>
 */
public final class Chronocycles {
    /**
     * Number of world-clock ticks in one Minecraft day.
     */
    public static final int TICKS_PER_DAY = 24_000;

    private static Chronocycles instance;
    private static boolean eventsRegistered;

    private final MinecraftServer server;
    private final ServerLevel overworld;
    private final Holder<WorldClock> overworldClock;
    private final NavigableMap<Long, ScheduledWeatherEvent> scheduledWeatherEvents = new TreeMap<>();

    private @Nullable ScheduledWeatherEvent activeWeatherEvent;
    private int timeScaleTicks;

    private Chronocycles(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.overworld = Objects.requireNonNull(server.overworld(), "Overworld has not been initialized.");
        this.overworldClock = this.overworld.dimensionType().defaultClock().orElseThrow(() -> new IllegalStateException("The overworld does not have a default clock."));
    }

    /**
     * Initializes the Chronocycles runtime for the specified server.
     *
     * <p>This method must be called after the server levels have been created,
     * such as from {@code ServerLifecycleEvents.SERVER_STARTED}.</p>
     *
     * @param server the active Minecraft server
     */
    public static void initialize(MinecraftServer server) {
        instance = new Chronocycles(server);
        instance.configureGameRules();
        registerEvents();
    }

    /**
     * Returns the active Chronocycles instance.
     *
     * @return the active instance
     * @throws IllegalStateException if Chronocycles has not been initialized
     */
    public static Chronocycles getInstance() {
        if (instance == null) throw new IllegalStateException("Chronocycles has not been initialized.");
        return instance;
    }

    /**
     * Schedules a weather event at the specified day and time.
     *
     * @param day the absolute world day, starting at {@code 0}
     * @param timeOfDay the time within the day, from {@code 0} to {@code 23999}
     * @param weather the weather to apply
     * @param duration the event duration in world-clock ticks
     * @return the created weather event
     * @throws IllegalArgumentException if the time is invalid, in the past, or overlaps another event
     * @throws ArithmeticException if the calculated world tick overflows
     */
    public ScheduledWeatherEvent scheduleWeather(long day, int timeOfDay, WeatherType weather, int duration) {
        Objects.requireNonNull(weather, "weather");

        if (duration <= 0) throw new IllegalArgumentException("Duration must be greater than zero.");

        long startTick = toWorldTick(day, timeOfDay);
        long endTick = Math.addExact(startTick, duration);

        if (startTick <= this.getCurrentTime()) {
            throw new IllegalArgumentException("Cannot schedule a weather event in the past.");
        }

        ScheduledWeatherEvent event = new ScheduledWeatherEvent(startTick, endTick, weather);
        this.validateNoOverlap(event);
        this.scheduledWeatherEvents.put(startTick, event);
        return event;
    }

    /**
     * Cancels the weather event scheduled at the specified day and time.
     *
     * <p>This method does not cancel an event that has already started.</p>
     *
     * @param day the absolute world day
     * @param timeOfDay the time within the day
     * @return {@code true} if an event was removed
     */
    public boolean cancelWeatherEvent(long day, int timeOfDay) {
        return this.scheduledWeatherEvents.remove(toWorldTick(day, timeOfDay)) != null;
    }

    /**
     * Removes all pending weather events.
     *
     * <p>The currently active event, if any, is not interrupted.</p>
     */
    public void clearWeatherSchedule() {
        this.scheduledWeatherEvents.clear();
    }

    /**
     * Returns the currently configured natural weather cycle mode.
     *
     * @return the configured weather cycle mode
     */
    public WeatherCycleMode getWeatherCycleMode() {
        int id = this.overworld.getGameRules().get(ChronocyclesGameRules.WEATHER_CYCLE_MODE);
        return WeatherCycleMode.byId(id);
    }

    /**
     * Changes how natural weather cycles advance.
     *
     * @param mode the new weather cycle mode
     */
    public void setWeatherCycleMode(WeatherCycleMode mode) {
        Objects.requireNonNull(mode, "mode");

        this.overworld.getGameRules().set(
                ChronocyclesGameRules.WEATHER_CYCLE_MODE,
                mode.getId(),
                this.server
        );

        this.updateWeatherAdvancement(mode == WeatherCycleMode.VANILLA);
    }

    private void tick() {
        boolean timeAdvanced = this.tickTime();
        this.tickWeather(timeAdvanced);
    }

    private boolean tickTime() {
        int timeScale = this.overworld.getGameRules().get(ChronocyclesGameRules.TIME_SCALE);

        if (timeScale == 0) {
            this.timeScaleTicks = 0;
            return false;
        }

        if (++this.timeScaleTicks < timeScale) return false;

        this.timeScaleTicks = 0;
        this.overworld.clockManager().addTicks(this.overworldClock, 1);
        return true;
    }

    private void tickWeather(boolean timeAdvanced) {
        long currentTime = this.getCurrentTime();

        if (this.activeWeatherEvent != null && currentTime >= this.activeWeatherEvent.endTick()) {
            this.activeWeatherEvent = null;
            this.applyWeather(WeatherType.CLEAR);
        }

        this.activateScheduledWeather(currentTime);

        if (this.activeWeatherEvent != null) {
            this.updateWeatherAdvancement(false);
            this.applyWeather(this.activeWeatherEvent.weather());
            return;
        }

        switch (this.getWeatherCycleMode()) {
            case NEVER -> this.updateWeatherAdvancement(false);
            case SCALED -> this.updateWeatherAdvancement(timeAdvanced);
            case VANILLA -> this.updateWeatherAdvancement(true);
        }
    }

    private void activateScheduledWeather(long currentTime) {
        Map.Entry<Long, ScheduledWeatherEvent> entry;
        while ((entry = this.scheduledWeatherEvents.firstEntry()) != null && entry.getKey() <= currentTime) {
            ScheduledWeatherEvent event = this.scheduledWeatherEvents.pollFirstEntry().getValue();
            if (currentTime < event.endTick()) this.activeWeatherEvent = event;
        }
    }

    private void applyWeather(WeatherType weather) {
        WeatherData weatherData = this.overworld.getWeatherData();
        weatherData.setClearWeatherTime(0);

        switch (weather) {
            case CLEAR -> {
                weatherData.setRainTime(0);
                weatherData.setThunderTime(0);
                weatherData.setRaining(false);
                weatherData.setThundering(false);
            }
            case RAIN -> {
                weatherData.setRainTime(Integer.MAX_VALUE);
                weatherData.setThunderTime(Integer.MAX_VALUE);
                weatherData.setRaining(true);
                weatherData.setThundering(false);
            }
            case THUNDER -> {
                weatherData.setRainTime(Integer.MAX_VALUE);
                weatherData.setThunderTime(Integer.MAX_VALUE);
                weatherData.setRaining(true);
                weatherData.setThundering(true);
            }
        }
    }

    private void validateNoOverlap(ScheduledWeatherEvent event) {
        if (this.activeWeatherEvent != null && overlaps(event, this.activeWeatherEvent)) {
            throw new IllegalArgumentException("The weather event overlaps the currently active event.");
        }

        Map.Entry<Long, ScheduledWeatherEvent> previous = this.scheduledWeatherEvents.floorEntry(event.startTick());
        if (previous != null && overlaps(event, previous.getValue())) {
            throw new IllegalArgumentException("The weather event overlaps an existing event.");
        }

        Map.Entry<Long, ScheduledWeatherEvent> next = this.scheduledWeatherEvents.ceilingEntry(event.startTick());
        if (next != null && overlaps(event, next.getValue())) {
            throw new IllegalArgumentException("The weather event overlaps an existing event.");
        }
    }

    private long getCurrentTime() {
        return this.overworld.getOverworldClockTime();
    }

    private void updateWeatherAdvancement(boolean enabled) {
        GameRules gameRules = this.overworld.getGameRules();
        if (gameRules.get(GameRules.ADVANCE_WEATHER) == enabled) return;

        gameRules.set(GameRules.ADVANCE_WEATHER, enabled, this.server);
    }

    private void configureGameRules() {
        this.overworld.getGameRules().set(GameRules.ADVANCE_TIME, false, this.server);
        this.updateWeatherAdvancement(this.getWeatherCycleMode() == WeatherCycleMode.VANILLA);
    }

    private static long toWorldTick(long day, int timeOfDay) {
        if (day < 0L) throw new IllegalArgumentException("Day cannot be negative.");
        if (timeOfDay < 0 || timeOfDay >= TICKS_PER_DAY) {
            throw new IllegalArgumentException("Time of day must be between 0 and 23999.");
        }

        return Math.addExact(Math.multiplyExact(day, TICKS_PER_DAY), timeOfDay);
    }

    private static boolean overlaps(ScheduledWeatherEvent first, ScheduledWeatherEvent second) {
        return first.startTick() < second.endTick() && first.endTick() > second.startTick();
    }

    private static void registerEvents() {
        if (eventsRegistered) return;

        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            Chronocycles chronocycles = instance;
            if (chronocycles != null && level == chronocycles.overworld) chronocycles.tick();
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> instance = null);
        eventsRegistered = true;
    }
}