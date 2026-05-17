package com.culltag.mixin;

import com.culltag.NametagController;
import com.culltag.NametagManager;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mixed into {@link ServerCommonPacketListenerImpl} — the class that owns both
 * the {@code connection} field and the {@code send(Packet, ChannelFutureListener)}
 * method. Targeting the parent rather than the subclass lets us shadow the field
 * and inject into the method without Mixin failing to locate them.
 *
 * <p>Because this mixin implements {@link NametagController} on the parent class,
 * the interface is automatically available on all subclass instances (including
 * {@code ServerGamePacketListenerImpl}). {@link com.culltag.NametagManager} casts
 * {@code player.connection} to {@code NametagController} to reach the per-connection
 * hidden-entity set and the direct-send bypass.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin implements NametagController {

    // connection is declared on this class — shadow works without an access widener.
    @Shadow protected Connection connection;

    @Unique
    private final Set<Integer> culltag_hiddenEntityIds = new HashSet<>();

    // ── NametagController ─────────────────────────────────────────────────────

    @Override
    public Set<Integer> culltag_getHiddenEntityIds() {
        return culltag_hiddenEntityIds;
    }

    /**
     * Sends directly via {@link Connection} to bypass the intercept inject below,
     * preventing infinite recursion when {@link NametagManager} pushes override packets.
     */
    @Override
    public void culltag_sendDirect(Packet<?> packet) {
        connection.send(packet, null);
    }

    // ── Packet intercept (HIDE_NAMETAG) ──────────────────────────────────────

    /**
     * Intercepts metadata packets for entities in the viewer's hidden set and
     * ORs the SNEAKING flag (0x02) into the FLAGS byte. Only fires when:
     * <ul>
     *   <li>mode is {@link CullTagConfig.Mode#HIDE_NAMETAG}</li>
     *   <li>the packet is a {@link ClientboundSetEntityDataPacket}</li>
     *   <li>the entity ID is in {@link #culltag_hiddenEntityIds}</li>
     *   <li>the FLAGS byte (id 0) is actually present in this delta update</li>
     * </ul>
     * Modified packets are re-sent via {@link Connection#send} directly.
     */
    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void culltag_interceptMetadata(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        if (!(packet instanceof ClientboundSetEntityDataPacket dataPacket)) return;
        if (!culltag_hiddenEntityIds.contains(dataPacket.id())) return;

        List<SynchedEntityData.DataValue<?>> original = dataPacket.packedItems();
        if (original == null) return;

        // Only rewrite when FLAGS is in this delta; client keeps sneaking state otherwise.
        boolean hasFlags = false;
        for (SynchedEntityData.DataValue<?> v : original) {
            if (v.id() == 0) { hasFlags = true; break; }
        }
        if (!hasFlags) return;

        ci.cancel();
        connection.send(new ClientboundSetEntityDataPacket(dataPacket.id(), rewriteFlags(original)), listener);
    }

    @SuppressWarnings("unchecked")
    private static List<SynchedEntityData.DataValue<?>> rewriteFlags(
            List<SynchedEntityData.DataValue<?>> entries) {

        List<SynchedEntityData.DataValue<?>> result = new ArrayList<>(entries.size());
        for (SynchedEntityData.DataValue<?> entry : entries) {
            if (entry.id() == 0) {
                byte flags = NametagManager.addSneaking(
                        ((SynchedEntityData.DataValue<Byte>) entry).value());
                result.add(new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, flags));
            } else {
                result.add(entry);
            }
        }
        return result;
    }
}
