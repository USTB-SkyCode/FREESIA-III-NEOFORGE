package com.nguyendevs.freesia.neoforgeworker;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.common.Mod;

/**
 * Stripped-down Worker node for the Freesia NeoForge proxy.
 * <p>
 * The actual behaviour lives entirely in the mixins (keep-alive bypass, no world ticking,
 * null player data, packet filtering). This class only holds the server instance for those
 * mixins to reach.
 */
@Mod(FreesiaNeoForgeWorker.MODID)
public class FreesiaNeoForgeWorker {

    public static final String MODID = "freesianeoforgeworker";

    /** Set by {@code MinecraftServerMixin} during server construction. */
    public static volatile MinecraftServer SERVER_INST;

    public FreesiaNeoForgeWorker() {
    }
}
