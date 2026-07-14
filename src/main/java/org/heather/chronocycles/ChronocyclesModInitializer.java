package org.heather.chronocycles;

import org.heather.chronocycles.api.Chronocycles;
import org.heather.chronocycles.api.ChronocyclesGameRules;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChronocyclesModInitializer implements ModInitializer {
    public static final String MOD_ID = "chronocycles";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ChronocyclesGameRules.initialize();
        ServerLifecycleEvents.SERVER_STARTED.register(Chronocycles::initialize);
        LOGGER.info("Initialized.");
    }

    public static Identifier identifier(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}