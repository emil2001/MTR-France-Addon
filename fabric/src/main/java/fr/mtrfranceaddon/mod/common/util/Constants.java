package fr.mtrfranceaddon.mod.common.util;

/**
 * Taken from Joban-Client-Mod
 */

import org.mtr.mapping.holder.Identifier;

public class Constants {

    public static final String MOD_ID = "mtrfranceaddon";
    public static final String MOD_NAME = "MTR France Addon";
    public static final String MOD_VERSION = "1.2.0-gtnh.0";
    // Native Forge channel names, including the namespace, are limited to 20 characters.
    public static final Identifier PACKET_ID = id("pkt");

    /**
     * Method to create an Identifier based on the MOD_ID.
     * @param id identifier of the ressource.
     * @return The generated identifier (ex: "mtrfranceaddon:example_block")
     */
    public static Identifier id(String id) {
        return new Identifier(MOD_ID, id);
    }


    /**
     * MTR Constants
     */
    public static final String MTR_MOD_ID = org.mtr.mod.Init.MOD_ID;
    public static final String MTR_NTE_MOD_ID = org.mtr.mod.Init.MOD_ID_NTE;

}
