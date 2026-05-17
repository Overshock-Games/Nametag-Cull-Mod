package com.culltag;

import net.minecraft.network.protocol.Packet;

import java.util.Set;

/**
 * Duck-typing interface mixed into {@link net.minecraft.server.network.ServerGamePacketListenerImpl}
 * by {@link com.culltag.mixin.ServerGamePacketListenerImplMixin}.
 *
 * <p>Exposes two capabilities to {@link NametagManager}:
 * <ol>
 *   <li>{@link #culltag_getHiddenEntityIds()} — entity IDs for which the sneaking-flag
 *       override is active. {@link com.culltag.mixin.ServerCommonPacketListenerImplMixin}
 *       consults this on every outgoing metadata packet.</li>
 *   <li>{@link #culltag_sendDirect(Packet)} — sends a packet via the raw
 *       {@link net.minecraft.network.Connection}, bypassing the mixin intercept so
 *       we don't recurse when pushing override/restore packets.</li>
 * </ol>
 */
public interface NametagController {

    /** Entity IDs whose outgoing metadata is currently being rewritten to force sneaking. */
    Set<Integer> culltag_getHiddenEntityIds();

    /** Sends {@code packet} to the client, bypassing the mixin's own intercept. */
    void culltag_sendDirect(Packet<?> packet);
}
