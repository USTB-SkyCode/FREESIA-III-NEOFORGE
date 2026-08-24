package com.nguyendevs.freesia.neoforge;

import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

/**
 * Protocol constants for Yes Steve Model 2.6.5 (NeoForge 1.21.1).
 * <p>
 * Verified by decompiling {@code ysm-2.6.5-neoforge+mc1.21.1-release.jar}:
 * the channel is {@code yes_steve_model:2.6.0} (DOT form), version {@code "2.6.0"}.
 * <p>
 * The payload body is a NeoForge "SimpleChannel-like" wrapper: a VarInt discriminator
 * (packet id) followed by the message body. Packet ids match the Forge 1.20.1 build.
 */
public final class YsmConstants {

    private YsmConstants() {
    }

    public static final String CHANNEL_NAMESPACE = "yes_steve_model";
    public static final String CHANNEL_PATH = "2_6_0";
    public static final String CHANNEL = CHANNEL_NAMESPACE + ":" + CHANNEL_PATH;
    public static final String PROTOCOL_VERSION = "2.6.0";

    public static final MinecraftChannelIdentifier CHANNEL_KEY =
            MinecraftChannelIdentifier.create(CHANNEL_NAMESPACE, CHANNEL_PATH);

    // Packet discriminators (VarInt), same numbering as the Forge 1.20.1 YSM.
    public static final int S2C_MODEL_SYNC = 1;      // model binary, S -> C
    public static final int C2S_MODEL_SYNC = 2;      // model binary, C -> S
    public static final int S2C_MOLANG = 3;          // entity ids + expression
    public static final int S2C_SET_MODEL = 4;       // entityId + model + texture + state
    public static final int C2S_SWITCH_MODEL = 5;    // model switch request
    public static final int C2S_ANIMATION = 7;       // entityId + animation
    public static final int C2S_MOLANG_REQ = 17;     // expression + entityId
    public static final int S2C_ANIMATION = 21;      // entityId + animation (2.6: state)
    public static final int S2C_HANDSHAKE = 51;      // server -> client version
    public static final int C2S_HANDSHAKE = 52;      // client -> server version

    /** Netty channel attribute key used by YSM to store the negotiated version. */
    public static final String CHANNEL_VERSION_ATTR = "yes_steve_model_channel_version";
}
