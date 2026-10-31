package fr.mtrfranceaddon.mod.common;

import fr.mtrfranceaddon.mod.common.registry.MTRFranceAddonRegistry;
import fr.mtrfranceaddon.mod.common.registry.MTRFranceAddonRegistryClient;
import fr.mtrfranceaddon.mod.common.util.Constants;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Init {

    public static final Logger LOGGER = LogManager.getLogger(Constants.MOD_ID);

    public static void init() {
        LOGGER.info("Initializing {} v{}", Constants.MOD_NAME, Constants.MOD_VERSION);
        MTRFranceAddonRegistry.register();
    }

    public static void initClient() {
        MTRFranceAddonRegistryClient.register();
    }

}
