package io.github.pepe3012.chronocycles.api;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import io.github.pepe3012.chronocycles.ChronocyclesModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;

/**
 * Defines the gamerules provided by Chronocycles.
 *
 * <p>These gamerules control the rate at which world time and natural weather
 * cycles advance.</p>
 */
public final class ChronocyclesGameRules {
    /**
     * Controls how many server ticks must pass before world time advances by one tick.
     *
     * <p>A value of {@code 0} freezes world time, {@code 1} uses vanilla speed,
     * and higher values progressively slow the day-night cycle.</p>
     */
    public static final GameRule<Integer> TIME_SCALE = registerInt("time_scale", 1, 0, Integer.MAX_VALUE);

    /**
     * Controls how the natural weather cycle advances.
     *
     * <p>Supported values are:</p>
     *
     * <ul>
     *     <li>{@code 0}: weather does not advance.</li>
     *     <li>{@code 1}: weather advances according to {@link #TIME_SCALE}.</li>
     *     <li>{@code 2}: weather advances at vanilla speed.</li>
     * </ul>
     */
    public static final GameRule<Integer> WEATHER_CYCLE_MODE = registerInt("weather_cycle_mode", WeatherCycleMode.VANILLA.getId(), WeatherCycleMode.NEVER.getId(), WeatherCycleMode.VANILLA.getId());

    private ChronocyclesGameRules() {}

    /**
     * Loads and registers the Chronocycles gamerules.
     *
     * <p>This method should be called during mod initialization.</p>
     */
    public static void initialize() {}

    private static GameRule<Integer> registerInt(String path, int defaultValue, int minValue, int maxValue) {
        return Registry.register(
                BuiltInRegistries.GAME_RULE,
                ChronocyclesModInitializer.identifier(path),
                new GameRule<>(
                        GameRuleCategory.UPDATES,
                        GameRuleType.INT,
                        IntegerArgumentType.integer(minValue, maxValue),
                        GameRuleTypeVisitor::visitInteger,
                        Codec.intRange(minValue, maxValue),
                        value -> value,
                        defaultValue,
                        FeatureFlagSet.of()
                )
        );
    }
}